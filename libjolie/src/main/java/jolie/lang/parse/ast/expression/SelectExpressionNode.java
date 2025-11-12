package jolie.lang.parse.ast.expression;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.context.ParsingContext;

public class SelectExpressionNode extends OLSyntaxNode {
	private final SelectPathNode selectPath;
	private final OLSyntaxNode whereExpression;

	public SelectExpressionNode( ParsingContext context, SelectPathNode selectPath,
		OLSyntaxNode whereExpression ) {
		super( context );
		this.selectPath = selectPath;
		this.whereExpression = whereExpression;
	}

	public SelectPathNode selectPath() {
		return selectPath;
	}

	public OLSyntaxNode whereExpression() {
		return whereExpression;
	}

	@Override
	public < C, R > R accept( OLVisitor< C, R > visitor, C ctx ) {
		return visitor.visit( this, ctx );
	}
}
