package jolie.runtime.expression;

import jolie.process.TransformationReason;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import jolie.runtime.VariablePath;
import jolie.runtime.paths.NativePathCollector;
import java.util.List;
import java.util.ArrayList;

public class PathsExpression implements Expression {
	private final VariablePath pathSpec;
	private final int wildcardDepth;
	private final String recursiveField;
	private final String arrayWildcardPath;
	private final int wildcardDepthAfterArray;
	private final boolean recursiveFieldIsArray;
	private final Expression whereExpression;

	public PathsExpression( VariablePath pathSpec, int wildcardDepth,
		Expression whereExpression ) {
		this( pathSpec, wildcardDepth, null, null, 0, false, whereExpression );
	}

	public PathsExpression( VariablePath pathSpec, int wildcardDepth, String recursiveField,
		Expression whereExpression ) {
		this( pathSpec, wildcardDepth, recursiveField, null, 0, false, whereExpression );
	}

	public PathsExpression( VariablePath pathSpec, int wildcardDepth, String recursiveField,
		String arrayWildcardPath, Expression whereExpression ) {
		this( pathSpec, wildcardDepth, recursiveField, arrayWildcardPath, 0, false, whereExpression );
	}

	public PathsExpression( VariablePath pathSpec, int wildcardDepth, String recursiveField,
		String arrayWildcardPath, int wildcardDepthAfterArray, Expression whereExpression ) {
		this( pathSpec, wildcardDepth, recursiveField, arrayWildcardPath, wildcardDepthAfterArray, false,
			whereExpression );
	}

	public PathsExpression( VariablePath pathSpec, int wildcardDepth, String recursiveField,
		String arrayWildcardPath, int wildcardDepthAfterArray, boolean recursiveFieldIsArray,
		Expression whereExpression ) {
		this.pathSpec = pathSpec;
		this.wildcardDepth = wildcardDepth;
		this.recursiveField = recursiveField;
		this.arrayWildcardPath = arrayWildcardPath;
		this.wildcardDepthAfterArray = wildcardDepthAfterArray;
		this.recursiveFieldIsArray = recursiveFieldIsArray;
		this.whereExpression = whereExpression;
	}

	@Override
	public Expression cloneExpression( TransformationReason reason ) {
		return new PathsExpression(
			(VariablePath) pathSpec.cloneExpression( reason ),
			wildcardDepth,
			recursiveField,
			arrayWildcardPath,
			wildcardDepthAfterArray,
			recursiveFieldIsArray,
			whereExpression.cloneExpression( reason ) );
	}

	@Override
	public Value evaluate() {
		String rootPath = extractRootPath( pathSpec );
		ValueVector vec = pathSpec.getValueVector();

		// Use native path collector
		List< String > candidatePaths;
		if( arrayWildcardPath != null && wildcardDepth > 0 ) {
			// Combined wildcard + array: var.*[*], var.*.*[*]
			// arrayWildcardPath is "" (empty string) to signal this combination
			candidatePaths = NativePathCollector.collectWildcardArrayPaths( vec, rootPath, wildcardDepth );
		} else if( arrayWildcardPath != null && wildcardDepthAfterArray > 0 ) {
			// Array wildcard followed by field wildcard: var[*].*, var[*].*.*
			// arrayWildcardPath is "" (base variable array)
			candidatePaths =
				NativePathCollector.collectArrayWildcardPaths( vec, rootPath, wildcardDepthAfterArray );
		} else if( arrayWildcardPath != null ) {
			// Array wildcard: data[*] or tree.items[*]
			candidatePaths = NativePathCollector.collectArrayPaths( vec, rootPath, arrayWildcardPath );
		} else if( recursiveField != null && recursiveFieldIsArray ) {
			// Recursive field with array wildcard: var..field[*]
			candidatePaths =
				NativePathCollector.collectRecursiveArrayPaths( vec, rootPath, recursiveField );
		} else if( recursiveField != null ) {
			// Recursive field search: var..field
			candidatePaths = NativePathCollector.collectPathsRecursive( vec, rootPath, recursiveField );
		} else {
			// Wildcard or simple path: var, var.*, var.*.*
			candidatePaths = NativePathCollector.collectPaths( vec, rootPath, wildcardDepth );
		}

		// Filter candidates using native Jolie WHERE expression
		List< String > matchingPaths = new ArrayList<>();
		List< CurrentValueExpression > currentValueExprs = new ArrayList<>();
		findAllCurrentValueExpressions( whereExpression, currentValueExprs );

		for( String path : candidatePaths ) {
			Value candidateValue = getValueAtPath( vec, path, rootPath );
			if( candidateValue == null )
				continue; // Path doesn't exist, skip

			// Bind all CurrentValueExpression instances to the candidate value
			for( CurrentValueExpression expr : currentValueExprs ) {
				expr.setCurrentNode( candidateValue );
			}

			Value whereResult = whereExpression.evaluate();
			if( whereResult.boolValue() ) {
				matchingPaths.add( path );
			}
		}

		// Return results as Value array
		Value result = Value.create();
		for( int i = 0; i < matchingPaths.size(); i++ ) {
			result.getChildren( "results" ).get( i ).setValue( matchingPaths.get( i ) );
		}
		return result;
	}

	private void findAllCurrentValueExpressions( Expression expr, List< CurrentValueExpression > result ) {
		if( expr instanceof CurrentValueExpression ) {
			result.add( (CurrentValueExpression) expr );
			return;
		}

		// Traverse comparison expressions
		if( expr instanceof jolie.runtime.expression.CompareCondition ) {
			jolie.runtime.expression.CompareCondition cmp = (jolie.runtime.expression.CompareCondition) expr;
			findAllCurrentValueExpressions( cmp.leftExpression(), result );
			findAllCurrentValueExpressions( cmp.rightExpression(), result );
			return;
		}

		// Traverse boolean operators
		if( expr instanceof jolie.runtime.expression.AndCondition ) {
			jolie.runtime.expression.AndCondition and = (jolie.runtime.expression.AndCondition) expr;
			for( Expression child : and.children ) {
				findAllCurrentValueExpressions( child, result );
			}
			return;
		}

		if( expr instanceof jolie.runtime.expression.OrCondition ) {
			jolie.runtime.expression.OrCondition or = (jolie.runtime.expression.OrCondition) expr;
			for( Expression child : or.children ) {
				findAllCurrentValueExpressions( child, result );
			}
			return;
		}

		if( expr instanceof jolie.runtime.expression.NotExpression ) {
			jolie.runtime.expression.NotExpression not = (jolie.runtime.expression.NotExpression) expr;
			findAllCurrentValueExpressions( not.expression, result );
			return;
		}
	}

	private Value getValueAtPath( ValueVector vec, String fullPath, String rootPath ) {
		// Remove root path prefix if present
		String relativePath = fullPath;
		if( rootPath != null && !rootPath.isEmpty() && fullPath.startsWith( rootPath ) ) {
			relativePath = fullPath.substring( rootPath.length() );
			if( relativePath.startsWith( "." ) ) {
				relativePath = relativePath.substring( 1 );
			}
		}

		// Navigate to the value
		Value current = vec.first();
		if( relativePath.isEmpty() ) {
			return current;
		}

		// Special case: if relativePath starts with [, it's a direct array access on vec
		if( relativePath.startsWith( "[" ) ) {
			int closeBracket = relativePath.indexOf( ']' );
			int index = Integer.parseInt( relativePath.substring( 1, closeBracket ) );
			if( index >= vec.size() )
				return null;
			current = vec.get( index );

			// Check if there's more path after the array index
			if( closeBracket + 1 < relativePath.length() ) {
				// There's more - extract it (skip the dot if present)
				String remaining = relativePath.substring( closeBracket + 1 );
				if( remaining.startsWith( "." ) ) {
					remaining = remaining.substring( 1 );
				}
				// Continue navigation with the remaining path
				relativePath = remaining;
			} else {
				// No more path - return the array element
				return current;
			}
		}

		String[] parts = relativePath.split( "\\." );
		for( String part : parts ) {
			// Handle array indices like "items[0]"
			if( part.contains( "[" ) ) {
				int bracketPos = part.indexOf( '[' );
				String fieldName = part.substring( 0, bracketPos );
				int index = Integer.parseInt( part.substring( bracketPos + 1, part.indexOf( ']' ) ) );

				// Check existence before accessing (avoid vivification)
				if( !current.hasChildren( fieldName ) )
					return null;
				ValueVector children = current.getChildren( fieldName );
				if( index >= children.size() )
					return null;
				current = children.get( index );
			} else {
				// Check existence before accessing (avoid vivification)
				if( !current.hasChildren( part ) )
					return null;
				current = current.getFirstChild( part );
			}
		}
		return current;
	}

	private String extractRootPath( VariablePath vp ) {
		if( vp.path().length == 0 )
			return "";

		StringBuilder path = new StringBuilder();
		for( int i = 0; i < vp.path().length; i++ ) {
			if( i > 0 )
				path.append( '.' );
			path.append( vp.path()[ i ].key().evaluate().strValue() );
		}
		return path.toString();
	}
}
