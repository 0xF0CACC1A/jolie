package jolie.lang.parse.ast.expression;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.VariablePathNode;
import jolie.lang.parse.context.ParsingContext;

/**
 * AST node representing a PATHS path (e.g., var, var.*, var.*.*, var..field, var[*], var.field[*],
 * var..field[*]) Supports multiple wildcard levels for deep path specification, recursive field
 * lookup, and array element expansion.
 */
public class PathSpecNode extends OLSyntaxNode {
	private final VariablePathNode baseVariable;
	private final int wildcardDepth;
	private final String recursiveField;
	private final String arrayWildcardPath;
	private final int wildcardDepthAfterArray;
	private final boolean recursiveFieldIsArray;

	public PathSpecNode( ParsingContext context, VariablePathNode baseVariable, int wildcardDepth ) {
		this( context, baseVariable, wildcardDepth, null, null, 0, false );
	}

	public PathSpecNode( ParsingContext context, VariablePathNode baseVariable, int wildcardDepth,
		String recursiveField ) {
		this( context, baseVariable, wildcardDepth, recursiveField, null, 0, false );
	}

	public PathSpecNode( ParsingContext context, VariablePathNode baseVariable, int wildcardDepth,
		String recursiveField, String arrayWildcardPath ) {
		this( context, baseVariable, wildcardDepth, recursiveField, arrayWildcardPath, 0, false );
	}

	public PathSpecNode( ParsingContext context, VariablePathNode baseVariable, int wildcardDepth,
		String recursiveField, String arrayWildcardPath, int wildcardDepthAfterArray ) {
		this( context, baseVariable, wildcardDepth, recursiveField, arrayWildcardPath,
			wildcardDepthAfterArray, false );
	}

	public PathSpecNode( ParsingContext context, VariablePathNode baseVariable, int wildcardDepth,
		String recursiveField, String arrayWildcardPath, int wildcardDepthAfterArray,
		boolean recursiveFieldIsArray ) {
		super( context );
		this.baseVariable = baseVariable;
		this.wildcardDepth = wildcardDepth;
		this.recursiveField = recursiveField;
		this.arrayWildcardPath = arrayWildcardPath;
		this.wildcardDepthAfterArray = wildcardDepthAfterArray;
		this.recursiveFieldIsArray = recursiveFieldIsArray;
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

	public String arrayWildcardPath() {
		return arrayWildcardPath;
	}

	public int wildcardDepthAfterArray() {
		return wildcardDepthAfterArray;
	}

	public boolean recursiveFieldIsArray() {
		return recursiveFieldIsArray;
	}

	public boolean isRecursive() {
		return recursiveField != null;
	}

	public boolean isArrayWildcard() {
		return arrayWildcardPath != null;
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
