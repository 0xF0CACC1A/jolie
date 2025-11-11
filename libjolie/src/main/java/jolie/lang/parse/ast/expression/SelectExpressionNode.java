package jolie.lang.parse.ast.expression;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.VariablePathNode;
import jolie.lang.parse.context.ParsingContext;

public class SelectExpressionNode extends OLSyntaxNode {
	private final String selectQuery;
	private final VariablePathNode intoVariable;
	private final VariablePathNode fromVariable;
	private final String whereQuery;

	public SelectExpressionNode( ParsingContext context, String selectQuery,
		VariablePathNode intoVariable, VariablePathNode fromVariable, String whereQuery ) {
		super( context );
		this.selectQuery = selectQuery;
		this.intoVariable = intoVariable;
		this.fromVariable = fromVariable;
		this.whereQuery = whereQuery;
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

	public String whereQuery() {
		return whereQuery;
	}

	@Override
	public < C, R > R accept( OLVisitor< C, R > visitor, C ctx ) {
		return visitor.visit( this, ctx );
	}
}
