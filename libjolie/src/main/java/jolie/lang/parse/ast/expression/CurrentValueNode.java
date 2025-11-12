package jolie.lang.parse.ast.expression;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.context.ParsingContext;
import java.util.List;
import java.util.Collections;

/**
 * Represents the current value ($) in a SELECT WHERE expression. This special node is used to
 * reference the value being filtered during SELECT evaluation. Can optionally include field path
 * (e.g., $.field, $.field.subfield)
 */
public class CurrentValueNode extends OLSyntaxNode {
	private final List< String > fieldPath;

	public CurrentValueNode( ParsingContext context ) {
		super( context );
		this.fieldPath = Collections.emptyList();
	}

	public CurrentValueNode( ParsingContext context, List< String > fieldPath ) {
		super( context );
		this.fieldPath = fieldPath;
	}

	public List< String > fieldPath() {
		return fieldPath;
	}

	@Override
	public < C, R > R accept( OLVisitor< C, R > visitor, C ctx ) {
		return visitor.visit( this, ctx );
	}
}
