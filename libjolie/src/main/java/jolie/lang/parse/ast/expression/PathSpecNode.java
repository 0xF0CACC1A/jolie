package jolie.lang.parse.ast.expression;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.VariablePathNode;
import jolie.lang.parse.context.ParsingContext;

/**
 * AST node representing a PATHS path (e.g., var, var.*, var.*.*, var..field) Supports multiple
 * wildcard levels for deep path specification and recursive field lookup.
 */
public class PathSpecNode extends OLSyntaxNode {
	private final VariablePathNode baseVariable;
	private final int wildcardDepth;
	private final String recursiveField;

	public PathSpecNode( ParsingContext context, VariablePathNode baseVariable, int wildcardDepth ) {
		this( context, baseVariable, wildcardDepth, null );
	}

	public PathSpecNode( ParsingContext context, VariablePathNode baseVariable, int wildcardDepth,
		String recursiveField ) {
		super( context );
		this.baseVariable = baseVariable;
		this.wildcardDepth = wildcardDepth;
		this.recursiveField = recursiveField;
	}

	public VariablePathNode baseVariable() {
		return baseVariable;
	}

	public int wildcardDepth() {
		return wildcardDepth;
	}

	public String recursiveField() {
		return recursiveField;
	}

	public boolean isRecursive() {
		return recursiveField != null;
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
