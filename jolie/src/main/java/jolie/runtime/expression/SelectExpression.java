package jolie.runtime.expression;

import jolie.process.TransformationReason;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import jolie.runtime.VariablePath;
import jolie.runtime.select.NativePathCollector;
import java.util.List;
import java.util.ArrayList;

public class SelectExpression implements Expression {
	private final VariablePath selectPath;
	private final int wildcardDepth;
	private final Expression whereExpression;

	public SelectExpression( VariablePath selectPath, int wildcardDepth,
		Expression whereExpression ) {
		this.selectPath = selectPath;
		this.wildcardDepth = wildcardDepth;
		this.whereExpression = whereExpression;
	}

	@Override
	public Expression cloneExpression( TransformationReason reason ) {
		return new SelectExpression(
			(VariablePath) selectPath.cloneExpression( reason ),
			wildcardDepth,
			whereExpression.cloneExpression( reason ) );
	}

	@Override
	public Value evaluate() {
		String rootPath = extractRootPath( selectPath );
		ValueVector vec = selectPath.getValueVector();

		// Use native path collector instead of ANTLR
		List< String > candidatePaths = NativePathCollector.collectPaths( vec, rootPath, wildcardDepth );

		// Filter candidates using native Jolie WHERE expression
		List< String > matchingPaths = new ArrayList<>();
		CurrentValueExpression currentValueExpr = findCurrentValueExpression( whereExpression );

		for( String path : candidatePaths ) {
			Value candidateValue = getValueAtPath( vec, path, rootPath );
			if( candidateValue == null )
				continue; // Path doesn't exist, skip

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
