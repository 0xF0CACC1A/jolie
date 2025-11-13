package jolie.process;

import jolie.ExecutionThread;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import jolie.runtime.VariablePath;
import jolie.runtime.expression.Expression;
import jolie.runtime.expression.CurrentValueExpression;
import jolie.runtime.paths.NativePathCollector;
import java.util.List;
import java.util.ArrayList;

public class PathsProcess implements Process {
	private final VariablePath pathSpec;
	private final int wildcardDepth;
	private final String recursiveField;
	private final Expression whereExpression;

	public PathsProcess( VariablePath pathSpec, int wildcardDepth,
		Expression whereExpression ) {
		this( pathSpec, wildcardDepth, null, whereExpression );
	}

	public PathsProcess( VariablePath pathSpec, int wildcardDepth, String recursiveField,
		Expression whereExpression ) {
		this.pathSpec = pathSpec;
		this.wildcardDepth = wildcardDepth;
		this.recursiveField = recursiveField;
		this.whereExpression = whereExpression;
	}

	@Override
	public Process copy( TransformationReason reason ) {
		return new PathsProcess(
			(VariablePath) pathSpec.cloneExpression( reason ),
			wildcardDepth,
			recursiveField,
			whereExpression.cloneExpression( reason ) );
	}

	@Override
	public void run() {
		if( ExecutionThread.currentThread().isKilled() )
			return;

		String rootPath = extractRootPath( pathSpec );
		ValueVector vec = pathSpec.getValueVector();

		// Use native path collector
		List< String > candidatePaths;
		if( recursiveField != null ) {
			// Recursive field search: var..field
			candidatePaths = NativePathCollector.collectPathsRecursive( vec, rootPath, recursiveField );
		} else {
			// Wildcard or simple path: var, var.*, var.*.*
			candidatePaths = NativePathCollector.collectPaths( vec, rootPath, wildcardDepth );
		}

		// Filter candidates using native Jolie WHERE expression
		// Note: PATHS as a statement (without <<) has no effect since there's no INTO variable
		// Use PATHS as an expression with << operator for meaningful results
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

		// Results are computed but not stored (use PATHS expression with << instead)
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

	@Override
	public boolean isKillable() {
		return true;
	}
}
