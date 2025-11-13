package jolie.lang.parse.ast;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.expression.PathSpecNode;
import jolie.lang.parse.context.ParsingContext;

public class PathsStatement extends OLSyntaxNode {
	private final PathSpecNode pathSpec;
	private final OLSyntaxNode whereExpression;

	public PathsStatement( ParsingContext context, PathSpecNode pathSpec,
		OLSyntaxNode whereExpression ) {
		super( context );
		this.pathSpec = pathSpec;
		this.whereExpression = whereExpression;
	}

	public PathSpecNode pathSpec() {
		return pathSpec;
	}

	public OLSyntaxNode whereExpression() {
		return whereExpression;
	}

	@Override
	public < C, R > R accept( OLVisitor< C, R > visitor, C ctx ) {
		return visitor.visit( this, ctx );
	}
}
