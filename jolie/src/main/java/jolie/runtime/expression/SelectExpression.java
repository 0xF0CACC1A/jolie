package jolie.runtime.expression;

import jolie.process.TransformationReason;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import jolie.runtime.VariablePath;
import jolie.runtime.select.SelectQueryExecutor;
import java.util.List;

public class SelectExpression implements Expression {
	private final String selectQuery;
	private final VariablePath intoVariable;
	private final VariablePath fromVariable;
	private final String whereQuery;

	public SelectExpression( String selectQuery, VariablePath intoVariable,
		VariablePath fromVariable, String whereQuery ) {
		this.selectQuery = selectQuery;
		this.intoVariable = intoVariable;
		this.fromVariable = fromVariable;
		this.whereQuery = whereQuery;
	}

	@Override
	public Expression cloneExpression( TransformationReason reason ) {
		return new SelectExpression(
			selectQuery,
			(VariablePath) intoVariable.cloneExpression( reason ),
			(VariablePath) fromVariable.cloneExpression( reason ),
			whereQuery );
	}

	@Override
	public Value evaluate() {
		String rootPath = extractRootPath( fromVariable );
		ValueVector vec = fromVariable.getValueVector();
		Object source = vec.size() > 1 ? vec : vec.first();

		List< String > matchingPaths = SelectQueryExecutor.execute(
			source,
			selectQuery,
			whereQuery,
			rootPath );

		for( int i = 0; i < matchingPaths.size(); i++ ) {
			intoVariable.getValueVector().get( i ).setValue( matchingPaths.get( i ) );
		}

		Value result = Value.create();
		for( int i = 0; i < matchingPaths.size(); i++ ) {
			result.getChildren( "result" ).get( i ).setValue( matchingPaths.get( i ) );
		}
		return result;
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
