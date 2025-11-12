package jolie.lang.parse.ast.expression;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.context.ParsingContext;

/**
 * Represents the current value ($) in a SELECT WHERE expression. This special node is used to
 * reference the value being filtered during SELECT evaluation.
 */
public class CurrentValueNode extends OLSyntaxNode {

	public CurrentValueNode( ParsingContext context ) {
		super( context );
	}

	@Override
	public < C, R > R accept( OLVisitor< C, R > visitor, C ctx ) {
		return visitor.visit( this, ctx );
	}
}
