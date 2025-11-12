package jolie.lang.parse.ast;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.context.ParsingContext;

public class SelectStatement extends OLSyntaxNode {
	private final String selectQuery;
	private final VariablePathNode fromVariable;
	private final OLSyntaxNode whereExpression;

	public SelectStatement( ParsingContext context, String selectQuery,
		VariablePathNode fromVariable, OLSyntaxNode whereExpression ) {
		super( context );
		this.selectQuery = selectQuery;
		this.fromVariable = fromVariable;
		this.whereExpression = whereExpression;
	}

	public String selectQuery() {
		return selectQuery;
	}

	public VariablePathNode fromVariable() {
		return fromVariable;
	}

	public OLSyntaxNode whereExpression() {
		return whereExpression;
	}

	@Override
	public < C, R > R accept( OLVisitor< C, R > visitor, C ctx ) {
		return visitor.visit( this, ctx );
	}
}
