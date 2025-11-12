package jolie.lang.parse.ast.expression;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.VariablePathNode;
import jolie.lang.parse.context.ParsingContext;

/**
 * AST node representing a SELECT path (e.g., var.*) For now, only supports wildcard pattern: var.*
 */
public class SelectPathNode extends OLSyntaxNode {
	private final VariablePathNode baseVariable;
	private final boolean isWildcard;

	public SelectPathNode( ParsingContext context, VariablePathNode baseVariable, boolean isWildcard ) {
		super( context );
		this.baseVariable = baseVariable;
		this.isWildcard = isWildcard;
	}

	public VariablePathNode baseVariable() {
		return baseVariable;
	}

	public boolean isWildcard() {
		return isWildcard;
	}

	@Override
	public < C, R > R accept( OLVisitor< C, R > visitor, C ctx ) {
		return visitor.visit( this, ctx );
	}
}
