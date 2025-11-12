package jolie.runtime.expression;

import jolie.process.TransformationReason;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import jolie.runtime.VariablePath;
import jolie.runtime.select.SelectQueryExecutor;
import java.util.List;
import java.util.ArrayList;

public class SelectExpression implements Expression {
	private final VariablePath selectPath;
	private final boolean isWildcard;
	private final Expression whereExpression;

	public SelectExpression( VariablePath selectPath, boolean isWildcard,
		Expression whereExpression ) {
		this.selectPath = selectPath;
		this.isWildcard = isWildcard;
		this.whereExpression = whereExpression;
	}

	@Override
	public Expression cloneExpression( TransformationReason reason ) {
		return new SelectExpression(
			(VariablePath) selectPath.cloneExpression( reason ),
			isWildcard,
			whereExpression.cloneExpression( reason ) );
	}

	@Override
	public Value evaluate() {
		String rootPath = extractRootPath( selectPath );
		ValueVector vec = selectPath.getValueVector();
		Object source = vec.size() > 1 ? vec : vec.first();

		// Convert native path to ANTLR query string (for now, only wildcard supported)
		String selectQuery = isWildcard ? "$.*" : "$";

		// Execute SELECT query without WHERE filtering (pass null to match all)
		List< String > candidatePaths = SelectQueryExecutor.execute(
			source,
			selectQuery,
			null,
			rootPath );

		// Filter candidates using native Jolie WHERE expression
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

		// Return results as Value array
		Value result = Value.create();
		for( int i = 0; i < matchingPaths.size(); i++ ) {
			result.getChildren( "results" ).get( i ).setValue( matchingPaths.get( i ) );
		}
		return result;
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
}
