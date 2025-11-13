package jolie.runtime.expression;

import jolie.process.TransformationReason;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Represents the current value ($) in PATHS WHERE expressions. Can optionally include field path
 * (e.g., $.field, $.field.subfield, $.field[*]) or recursive field (e.g., $..field)
 */
public class CurrentValueExpression implements Expression {
	private Value currentNode;
	private final List< FieldPathComponent > fieldPathComponents;
	private final String recursiveField;

	/**
	 * Component of a field path, potentially with array wildcard. E.g., "tags" with [*] in $.tags[*]
	 */
	public static class FieldPathComponent {
		private final String fieldName;
		private final boolean hasArrayWildcard;

		public FieldPathComponent( String fieldName, boolean hasArrayWildcard ) {
			this.fieldName = fieldName;
			this.hasArrayWildcard = hasArrayWildcard;
		}

		public String fieldName() {
			return fieldName;
		}

		public boolean hasArrayWildcard() {
			return hasArrayWildcard;
		}
	}

	public CurrentValueExpression() {
		this.fieldPathComponents = Collections.emptyList();
		this.recursiveField = null;
	}

	// Legacy constructor for backward compatibility - converts String list to components
	public CurrentValueExpression( List< String > fieldPath ) {
		List< FieldPathComponent > components = new ArrayList<>();
		for( String fieldName : fieldPath ) {
			components.add( new FieldPathComponent( fieldName, false ) );
		}
		this.fieldPathComponents = components;
		this.recursiveField = null;
	}

	// New constructor with FieldPathComponent list
	public CurrentValueExpression( List< FieldPathComponent > fieldPathComponents, boolean isComponentList ) {
		this.fieldPathComponents = fieldPathComponents;
		this.recursiveField = null;
	}

	public CurrentValueExpression( String recursiveField ) {
		this.fieldPathComponents = Collections.emptyList();
		this.recursiveField = recursiveField;
	}

	public void setCurrentNode( Value node ) {
		this.currentNode = node;
	}

	@Override
	public Expression cloneExpression( TransformationReason reason ) {
		if( recursiveField != null ) {
			return new CurrentValueExpression( recursiveField );
		}
		return new CurrentValueExpression( fieldPathComponents, true );
	}

	@Override
	public Value evaluate() {
		if( currentNode == null )
			throw new IllegalStateException( "$ not bound" );

		// Recursive field search: $..field
		if( recursiveField != null ) {
			return searchRecursive( currentNode, recursiveField );
		}

		// If no field path, return current node directly
		if( fieldPathComponents.isEmpty() )
			return currentNode;

		// Check if any field has array wildcard
		boolean hasArrayWildcard = false;
		for( FieldPathComponent comp : fieldPathComponents ) {
			if( comp.hasArrayWildcard() ) {
				hasArrayWildcard = true;
				break;
			}
		}

		if( !hasArrayWildcard ) {
			// Simple case: no array wildcards, just navigate
			return navigateFieldPath( currentNode, fieldPathComponents );
		} else {
			// Complex case: has array wildcards
			// This shouldn't be called directly in comparisons
			throw new IllegalStateException(
				"Cannot evaluate $.field[*] directly; use evaluateArrayWildcardComparison()" );
		}
	}

	/**
	 * Navigate through field path without array wildcards. Uses getFirstChild() for each field.
	 */
	private Value navigateFieldPath( Value start, List< FieldPathComponent > path ) {
		Value result = start;
		for( FieldPathComponent component : path ) {
			if( component.hasArrayWildcard() ) {
				// This shouldn't happen in simple navigation
				throw new IllegalStateException( "Array wildcard in simple navigation" );
			}
			result = result.getFirstChild( component.fieldName() );
		}
		return result;
	}

	/**
	 * Check if this expression has array wildcards.
	 */
	public boolean hasArrayWildcards() {
		for( FieldPathComponent comp : fieldPathComponents ) {
			if( comp.hasArrayWildcard() ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Evaluate array wildcard comparison: $.field[*] op value Returns true if ANY array element
	 * satisfies the condition (existential quantification).
	 *
	 * @param comparisonValue The value to compare against
	 * @param operator Comparison operator as BiPredicate
	 * @return true if ANY element satisfies the condition
	 */
	public boolean evaluateArrayWildcardComparison( Value comparisonValue,
		java.util.function.BiPredicate< Value, Value > operator ) {
		if( currentNode == null )
			throw new IllegalStateException( "$ not bound" );

		return checkPathWithWildcard( currentNode, fieldPathComponents, 0, comparisonValue, operator );
	}

	/**
	 * Recursively check if any path through array wildcards satisfies the condition.
	 *
	 * @param current Current value node
	 * @param path Remaining field path components
	 * @param index Current index in path
	 * @param comparisonValue Value to compare against
	 * @param operator Comparison operator
	 * @return true if ANY path satisfies the condition
	 */
	private boolean checkPathWithWildcard( Value current, List< FieldPathComponent > path, int index,
		Value comparisonValue, java.util.function.BiPredicate< Value, Value > operator ) {
		// Base case: reached end of path
		if( index >= path.size() ) {
			return operator.test( current, comparisonValue );
		}

		FieldPathComponent component = path.get( index );
		String fieldName = component.fieldName();

		// Check if field exists (prevent vivification)
		if( !current.hasChildren( fieldName ) ) {
			return false; // Field doesn't exist, no match
		}

		if( !component.hasArrayWildcard() ) {
			// No wildcard: just navigate to first child
			Value next = current.getFirstChild( fieldName );
			return checkPathWithWildcard( next, path, index + 1, comparisonValue, operator );
		} else {
			// Has wildcard: check ALL elements in the array
			ValueVector children = current.getChildren( fieldName );

			// Existential quantification: return true if ANY element matches
			for( int i = 0; i < children.size(); i++ ) {
				Value element = children.get( i );
				if( checkPathWithWildcard( element, path, index + 1, comparisonValue, operator ) ) {
					return true; // Found a match!
				}
			}

			// No element matched
			return false;
		}
	}

	private Value searchRecursive( Value node, String targetField ) {
		// Stack-based iterative DFS to find first occurrence of field
		java.util.Stack< Value > stack = new java.util.Stack<>();
		stack.push( node );

		while( !stack.isEmpty() ) {
			Value current = stack.pop();

			// Check if this node has the target field
			if( current.hasChildren( targetField ) ) {
				return current.getFirstChild( targetField );
			}

			// Add all children to stack for further search
			current.children().forEach( ( fieldName, childVector ) -> {
				if( !childVector.isEmpty() ) {
					stack.push( childVector.first() );
				}
			} );
		}

		// Field not found, return undefined value
		return Value.UNDEFINED_VALUE;
	}
}
