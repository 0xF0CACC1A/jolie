package jolie.lang.parse.ast.expression;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.VariablePathNode;
import jolie.lang.parse.context.ParsingContext;

/**
 * AST node representing a SELECT path (e.g., var, var.*, var.*.*) Supports multiple wildcard levels
 * for deep selection.
 */
public class SelectPathNode extends OLSyntaxNode {
	private final VariablePathNode baseVariable;
	private final int wildcardDepth;

	public SelectPathNode( ParsingContext context, VariablePathNode baseVariable, int wildcardDepth ) {
		super( context );
		this.baseVariable = baseVariable;
		this.wildcardDepth = wildcardDepth;
	}

	public VariablePathNode baseVariable() {
		return baseVariable;
	}

	public int wildcardDepth() {
		return wildcardDepth;
	}

	// Backward compatibility method
	public boolean isWildcard() {
		return wildcardDepth > 0;
	}

	@Override
	public < C, R > R accept( OLVisitor< C, R > visitor, C ctx ) {
		return visitor.visit( this, ctx );
	}
}
