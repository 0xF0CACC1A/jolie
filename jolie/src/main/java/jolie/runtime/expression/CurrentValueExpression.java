package jolie.runtime.expression;

import jolie.process.TransformationReason;
import jolie.runtime.Value;
import java.util.List;
import java.util.Collections;

/**
 * Represents the current value ($) in SELECT WHERE expressions. Can optionally include field path
 * (e.g., $.field, $.field.subfield)
 */
public class CurrentValueExpression implements Expression {
	private Value currentNode;
	private final List< String > fieldPath;

	public CurrentValueExpression() {
		this.fieldPath = Collections.emptyList();
	}

	public CurrentValueExpression( List< String > fieldPath ) {
		this.fieldPath = fieldPath;
	}

	public void setCurrentNode( Value node ) {
		this.currentNode = node;
	}

	@Override
	public Expression cloneExpression( TransformationReason reason ) {
		return new CurrentValueExpression( fieldPath );
	}

	@Override
	public Value evaluate() {
		if( currentNode == null )
			throw new IllegalStateException( "$ not bound" );

		// If no field path, return current node directly
		if( fieldPath.isEmpty() )
			return currentNode;

		// Navigate through field path
		Value result = currentNode;
		for( String field : fieldPath ) {
			result = result.getFirstChild( field );
		}
		return result;
	}
}
