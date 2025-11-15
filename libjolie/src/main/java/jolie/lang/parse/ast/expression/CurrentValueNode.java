package jolie.lang.parse.ast.expression;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.context.ParsingContext;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Represents the current value ($) in a PATHS WHERE expression. This special node is used to
 * reference the value being filtered during PATHS evaluation. Can optionally include field path
 * (e.g., $.field, $.field.subfield, $.field[*]) or recursive field (e.g., $..field)
 */
public class CurrentValueNode extends OLSyntaxNode {
	private final List< FieldPathComponent > fieldPathComponents;
	private final String recursiveField;

	/**
	 * Component of a field path, potentially with field or array wildcard. Examples: "tags" in $.tags,
	 * "*" in $.*, "tags[*]" in $.tags[*], "*[*]" in $.*[*]
	 */
	public static class FieldPathComponent {
		private final String fieldName; // null if field wildcard
		private final boolean hasFieldWildcard;
		private final boolean hasArrayWildcard;

		// Constructor for regular field with optional array wildcard
		public FieldPathComponent( String fieldName, boolean hasArrayWildcard ) {
			this.fieldName = fieldName;
			this.hasFieldWildcard = false;
			this.hasArrayWildcard = hasArrayWildcard;
		}

		// Constructor for field wildcard with optional array wildcard
		public FieldPathComponent( boolean hasFieldWildcard, boolean hasArrayWildcard ) {
			this.fieldName = null;
			this.hasFieldWildcard = hasFieldWildcard;
			this.hasArrayWildcard = hasArrayWildcard;
		}

		public String fieldName() {
			return fieldName;
		}

		public boolean hasFieldWildcard() {
			return hasFieldWildcard;
		}

		public boolean hasArrayWildcard() {
			return hasArrayWildcard;
		}
	}

	public CurrentValueNode( ParsingContext context ) {
		super( context );
		this.fieldPathComponents = Collections.emptyList();
		this.recursiveField = null;
	}

	// Legacy constructor for backward compatibility - converts String list to components
	public CurrentValueNode( ParsingContext context, List< String > fieldPath ) {
		super( context );
		List< FieldPathComponent > components = new ArrayList<>();
		for( String fieldName : fieldPath ) {
			components.add( new FieldPathComponent( fieldName, false ) );
		}
		this.fieldPathComponents = components;
		this.recursiveField = null;
	}

	// New constructor with FieldPathComponent list
	public CurrentValueNode( ParsingContext context, List< FieldPathComponent > fieldPathComponents,
		boolean isComponentList ) {
		super( context );
		this.fieldPathComponents = fieldPathComponents;
		this.recursiveField = null;
	}

	public CurrentValueNode( ParsingContext context, String recursiveField ) {
		super( context );
		this.fieldPathComponents = Collections.emptyList();
		this.recursiveField = recursiveField;
	}

	// Legacy getter for backward compatibility - returns field names only
	public List< String > fieldPath() {
		List< String > result = new ArrayList<>();
		for( FieldPathComponent comp : fieldPathComponents ) {
			result.add( comp.fieldName() );
		}
		return result;
	}

	// New getter for structured components
	public List< FieldPathComponent > fieldPathComponents() {
		return fieldPathComponents;
	}

	public String recursiveField() {
		return recursiveField;
	}

	public boolean isRecursive() {
		return recursiveField != null;
	}

	public boolean hasArrayWildcards() {
		for( FieldPathComponent comp : fieldPathComponents ) {
			if( comp.hasArrayWildcard() ) {
				return true;
			}
		}
		return false;
	}

	public boolean hasFieldWildcards() {
		for( FieldPathComponent comp : fieldPathComponents ) {
			if( comp.hasFieldWildcard() ) {
				return true;
			}
		}
		return false;
	}

	@Override
	public < C, R > R accept( OLVisitor< C, R > visitor, C ctx ) {
		return visitor.visit( this, ctx );
	}
}
