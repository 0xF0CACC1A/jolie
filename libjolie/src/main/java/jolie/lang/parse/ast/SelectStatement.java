package jolie.lang.parse.ast;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.context.ParsingContext;

public class SelectStatement extends OLSyntaxNode {
	private final String selectQuery;
	private final VariablePathNode intoVariable;
	private final VariablePathNode fromVariable;
	private final OLSyntaxNode whereExpression;

	public SelectStatement( ParsingContext context, String selectQuery,
		VariablePathNode intoVariable, VariablePathNode fromVariable, OLSyntaxNode whereExpression ) {
		super( context );
		this.selectQuery = selectQuery;
		this.intoVariable = intoVariable;
		this.fromVariable = fromVariable;
		this.whereExpression = whereExpression;
	}

	public String selectQuery() {
		return selectQuery;
	}

	public VariablePathNode intoVariable() {
		return intoVariable;
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
