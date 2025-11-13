package jolie.lang.parse.ast.expression;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.context.ParsingContext;

public class PathsExpressionNode extends OLSyntaxNode {
	private final PathSpecNode pathSpec;
	private final OLSyntaxNode whereExpression;

	public PathsExpressionNode( ParsingContext context, PathSpecNode pathSpec,
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
