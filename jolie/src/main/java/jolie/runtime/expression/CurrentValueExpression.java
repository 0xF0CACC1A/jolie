package jolie.runtime.expression;

import jolie.process.TransformationReason;
import jolie.runtime.Value;
import java.util.List;
import java.util.Collections;

/**
 * Represents the current value ($) in PATHS WHERE expressions. Can optionally include field path
 * (e.g., $.field, $.field.subfield) or recursive field (e.g., $..field)
 */
public class CurrentValueExpression implements Expression {
	private Value currentNode;
	private final List< String > fieldPath;
	private final String recursiveField;

	public CurrentValueExpression() {
		this.fieldPath = Collections.emptyList();
		this.recursiveField = null;
	}

	public CurrentValueExpression( List< String > fieldPath ) {
		this.fieldPath = fieldPath;
		this.recursiveField = null;
	}

	public CurrentValueExpression( String recursiveField ) {
		this.fieldPath = Collections.emptyList();
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
		return new CurrentValueExpression( fieldPath );
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
		if( fieldPath.isEmpty() )
			return currentNode;

		// Navigate through field path
		Value result = currentNode;
		for( String field : fieldPath ) {
			result = result.getFirstChild( field );
		}
		return result;
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
