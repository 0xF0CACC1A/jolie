package jolie.lang.parse.ast;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.context.ParsingContext;

public class PrintStatement extends OLSyntaxNode {
	private final OLSyntaxNode expression;

	public PrintStatement( ParsingContext context, OLSyntaxNode expression ) {
		super( context );
		this.expression = expression;
	}

	public OLSyntaxNode expression() {
		return expression;
	}

	@Override
	public < C, R > R accept( OLVisitor< C, R > visitor, C ctx ) {
		return visitor.visit( this, ctx );
	}
}
