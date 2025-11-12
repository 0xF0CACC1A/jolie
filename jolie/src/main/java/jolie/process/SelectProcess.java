package jolie.process;

import jolie.ExecutionThread;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import jolie.runtime.VariablePath;
import jolie.runtime.expression.Expression;
import jolie.runtime.expression.CurrentValueExpression;
import jolie.runtime.select.SelectQueryExecutor;
import java.util.List;
import java.util.ArrayList;

public class SelectProcess implements Process {
	private final String selectQuery;
	private final VariablePath fromVariable;
	private final Expression whereExpression;

	public SelectProcess( String selectQuery,
		VariablePath fromVariable, Expression whereExpression ) {
		this.selectQuery = selectQuery;
		this.fromVariable = fromVariable;
		this.whereExpression = whereExpression;
	}

	@Override
	public Process copy( TransformationReason reason ) {
		return new SelectProcess(
			selectQuery,
			(VariablePath) fromVariable.cloneExpression( reason ),
			whereExpression.cloneExpression( reason ) );
	}

	@Override
	public void run() {
		if( ExecutionThread.currentThread().isKilled() )
			return;

		String rootPath = extractRootPath( fromVariable );
		ValueVector vec = fromVariable.getValueVector();
		Object source = vec.size() > 1 ? vec : vec.first();

		// Execute SELECT query without WHERE filtering (pass null to match all)
		List< String > candidatePaths = SelectQueryExecutor.execute(
			source,
			selectQuery,
			null,
			rootPath );

		// Filter candidates using native Jolie WHERE expression
		// Note: SELECT as a statement (without <<) has no effect since there's no INTO variable
		// Use SELECT as an expression with << operator for meaningful results
		List< String > matchingPaths = new ArrayList<>();
		CurrentValueExpression currentValueExpr = findCurrentValueExpression( whereExpression );

		for( String path : candidatePaths ) {
			Value candidateValue = getValueAtPath( vec, path, rootPath );
			if( currentValueExpr != null ) {
				currentValueExpr.setCurrentNode( candidateValue );
			}

			Value whereResult = whereExpression.evaluate();
			if( whereResult.boolValue() ) {
				matchingPaths.add( path );
			}
		}

		// Results are computed but not stored (use SELECT expression with << instead)
	}

	private CurrentValueExpression findCurrentValueExpression( Expression expr ) {
		if( expr instanceof CurrentValueExpression ) {
			return (CurrentValueExpression) expr;
		}
		// For comparison expressions, check operands
		if( expr instanceof jolie.runtime.expression.CompareCondition ) {
			jolie.runtime.expression.CompareCondition cmp = (jolie.runtime.expression.CompareCondition) expr;
			// Check left operand
			if( cmp.leftExpression() instanceof CurrentValueExpression ) {
				return (CurrentValueExpression) cmp.leftExpression();
			}
			// Check right operand
			if( cmp.rightExpression() instanceof CurrentValueExpression ) {
				return (CurrentValueExpression) cmp.rightExpression();
			}
		}
		// For other composite expressions, would need more traversal
		return null;
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
				current = current.getChildren( fieldName ).get( index );
			} else {
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
