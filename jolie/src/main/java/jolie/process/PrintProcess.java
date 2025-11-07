package jolie.process;

import jolie.ExecutionThread;
import jolie.runtime.Value;
import jolie.runtime.expression.Expression;

public class PrintProcess implements Process {
	private final Expression expression;

	public PrintProcess( Expression expression ) {
		this.expression = expression;
	}

	@Override
	public Process copy( TransformationReason reason ) {
		return new PrintProcess( expression.cloneExpression( reason ) );
	}

	@Override
	public void run() {
		if( ExecutionThread.currentThread().isKilled() )
			return;

		Value value = expression.evaluate();
		System.out.println( value.strValue() );
	}

	@Override
	public boolean isKillable() {
		return true;
	}
}
