package jolie.lang.parse.ast.expression;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.context.ParsingContext;
import java.util.List;
import java.util.Collections;

/**
 * Represents the current value ($) in a PATHS WHERE expression. This special node is used to
 * reference the value being filtered during PATHS evaluation. Can optionally include field path
 * (e.g., $.field, $.field.subfield) or recursive field (e.g., $..field)
 */
public class CurrentValueNode extends OLSyntaxNode {
	private final List< String > fieldPath;
	private final String recursiveField;

	public CurrentValueNode( ParsingContext context ) {
		super( context );
		this.fieldPath = Collections.emptyList();
		this.recursiveField = null;
	}

	public CurrentValueNode( ParsingContext context, List< String > fieldPath ) {
		super( context );
		this.fieldPath = fieldPath;
		this.recursiveField = null;
	}

	public CurrentValueNode( ParsingContext context, String recursiveField ) {
		super( context );
		this.fieldPath = Collections.emptyList();
		this.recursiveField = recursiveField;
	}

	public List< String > fieldPath() {
		return fieldPath;
	}

	public String recursiveField() {
		return recursiveField;
	}

	public boolean isRecursive() {
		return recursiveField != null;
	}

	@Override
	public < C, R > R accept( OLVisitor< C, R > visitor, C ctx ) {
		return visitor.visit( this, ctx );
	}
}
