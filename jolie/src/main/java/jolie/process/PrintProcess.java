package jolie.process;

import jolie.ExecutionThread;

public class PrintProcess implements Process {
	public PrintProcess() {}

	@Override
	public Process copy( TransformationReason reason ) {
		return new PrintProcess();
	}

	@Override
	public void run() {
		if( ExecutionThread.currentThread().isKilled() )
			return;

		// Mockup: print "hello" when PRINT is encountered
		System.out.println( "hello" );
	}

	@Override
	public boolean isKillable() {
		return true;
	}
}
