package jolie.runtime.expression;

import jolie.process.TransformationReason;
import jolie.runtime.Value;

/**
 * Represents the current value ($) in SELECT WHERE expressions.
 */
public class CurrentValueExpression implements Expression {
	private Value currentNode;

	public CurrentValueExpression() {}

	public void setCurrentNode( Value node ) {
		this.currentNode = node;
	}

	@Override
	public Expression cloneExpression( TransformationReason reason ) {
		return new CurrentValueExpression();
	}

	@Override
	public Value evaluate() {
		if( currentNode == null )
			throw new IllegalStateException( "$ not bound" );
		return currentNode;
	}
}
