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
	private final VariablePath intoVariable;
	private final VariablePath fromVariable;
	private final Expression whereExpression;

	public SelectProcess( String selectQuery, VariablePath intoVariable,
		VariablePath fromVariable, Expression whereExpression ) {
		this.selectQuery = selectQuery;
		this.intoVariable = intoVariable;
		this.fromVariable = fromVariable;
		this.whereExpression = whereExpression;
	}

	@Override
	public Process copy( TransformationReason reason ) {
		return new SelectProcess(
			selectQuery,
			(VariablePath) intoVariable.cloneExpression( reason ),
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
		List< String > matchingPaths = new ArrayList<>();
		CurrentValueExpression currentValueExpr = findCurrentValueExpression( whereExpression );
		System.out.println( "DEBUG: whereExpression class = " + whereExpression.getClass().getName() );
		System.out.println( "DEBUG: currentValueExpr = " + currentValueExpr );
		System.out.println( "DEBUG: candidatePaths = " + candidatePaths );

		for( String path : candidatePaths ) {
			Value candidateValue = getValueAtPath( vec, path, rootPath );
			System.out.println( "DEBUG: Checking path=" + path + ", value=" + candidateValue.intValue() );
			if( currentValueExpr != null ) {
				currentValueExpr.setCurrentNode( candidateValue );
				System.out.println( "DEBUG: Set currentNode to " + candidateValue.intValue() );
			}

			Value whereResult = whereExpression.evaluate();
			System.out.println( "DEBUG: WHERE result = " + whereResult.boolValue() );
			if( whereResult.boolValue() ) {
				matchingPaths.add( path );
			}
		}

		// Store results
		for( int i = 0; i < matchingPaths.size(); i++ ) {
			intoVariable.getValueVector().get( i ).setValue( matchingPaths.get( i ) );
		}
	}

	private CurrentValueExpression findCurrentValueExpression( Expression expr ) {
		if( expr instanceof CurrentValueExpression ) {
			return (CurrentValueExpression) expr;
		}
		// For comparison expressions, check operands
		if( expr instanceof jolie.runtime.expression.CompareCondition ) {
			jolie.runtime.expression.CompareCondition cmp = (jolie.runtime.expression.CompareCondition) expr;
			System.out.println( "DEBUG: Found CompareCondition" );
			System.out.println( "DEBUG: left = " + cmp.leftExpression().getClass().getName() );
			System.out.println( "DEBUG: right = " + cmp.rightExpression().getClass().getName() );
			// Check left operand
			if( cmp.leftExpression() instanceof CurrentValueExpression ) {
				System.out.println( "DEBUG: Found CurrentValueExpression on left" );
				return (CurrentValueExpression) cmp.leftExpression();
			}
			// Check right operand
			if( cmp.rightExpression() instanceof CurrentValueExpression ) {
				System.out.println( "DEBUG: Found CurrentValueExpression on right" );
				return (CurrentValueExpression) cmp.rightExpression();
			}
		}
		// For other composite expressions, would need more traversal
		System.out.println( "DEBUG: CurrentValueExpression not found, returning null" );
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
