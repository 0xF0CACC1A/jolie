package jolie.runtime.select;

import jolie.lang.parse.select.*;
import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import org.antlr.v4.runtime.*;
import java.util.*;

/**
 * Executes SELECT queries on Jolie Value trees and ValueVectors Returns list of matching path
 * strings
 */
public class SelectQueryExecutor {

	public static List< String > execute( Object source, String selectQuery, String whereQuery, String rootPath ) {
		// Validate source type
		if( !(source instanceof Value) && !(source instanceof ValueVector) ) {
			throw new IllegalArgumentException(
				"Source must be Value or ValueVector, got: " + source.getClass().getName() );
		}

		// Parse SELECT clause
		SelectQueryParser selectParser = new SelectQueryParser( new CommonTokenStream(
			new SelectQueryLexer( CharStreams.fromString( selectQuery ) ) ) );
		SelectQueryParser.SelectClauseContext selectCtx = selectParser.selectClause();

		// Check if using $[*] syntax (SelectWithArray) vs $ syntax (SelectTree)
		boolean isArraySelect = selectCtx instanceof SelectQueryParser.SelectWithArrayContext;

		// Extract segments - these define the path navigation ($.field, $..name, etc.)
		List< SelectQueryParser.SegmentContext > segments = switch( selectCtx ) {
		case SelectQueryParser.SelectWithArrayContext c -> c.segment();
		case SelectQueryParser.SelectTreeContext c -> c.segment();
		default -> throw new IllegalStateException( "Unknown select clause type" );
		};

		// Parse WHERE clause if provided
		WhereEvaluator whereEval = null;
		if( whereQuery != null && !whereQuery.trim().isEmpty() ) {
			SelectQueryParser whereParser = new SelectQueryParser( new CommonTokenStream(
				new SelectQueryLexer( CharStreams.fromString( whereQuery.trim() ) ) ) );
			whereEval = new WhereEvaluator( whereParser.whereClause() );
		}

		// Execute query
		List< String > results = new ArrayList<>();
		Deque< PathNode > stack = new ArrayDeque<>();

		// Initialize stack based on source type and query syntax
		switch( source ) {
		case ValueVector arr -> {
			// For ValueVector, require explicit $[*] to iterate
			if( isArraySelect ) {
				pushArray( stack, arr, rootPath, 0 );
			}
			// If no $[*], return empty results
		}
		case Value tree -> {
			// For tree, $[*] adds [0] to path (root = root[0])
			String treePath = isArraySelect ? rootPath + "[0]" : rootPath;
			stack.push( new PathNode( tree, treePath, 0 ) );
		}
		default -> throw new IllegalStateException();
		}

		// Navigate through segments
		while( !stack.isEmpty() ) {
			PathNode current = stack.pop();

			// Terminal: test WHERE and collect
			if( current.segmentIndex >= segments.size() ) {
				if( whereEval == null || whereEval.test( current.node ) ) {
					results.add( current.path );
				}
				continue;
			}

			// Process current segment
			switch( segments.get( current.segmentIndex ) ) {
			case SelectQueryParser.DescendantArraySegmentContext seg ->
				descendant( current, seg.ID().getText(), true, stack );
			case SelectQueryParser.DescendantSegmentContext seg ->
				descendant( current, seg.ID().getText(), false, stack );
			case SelectQueryParser.DotSegmentContext seg ->
				dot( current, seg.token(), stack );
			default -> throw new IllegalStateException( "Unknown segment type" );
			}
		}

		return results;
	}

	// ========== PATH NAVIGATION ==========

	private static void dot( PathNode current, SelectQueryParser.TokenContext token, Deque< PathNode > stack ) {
		int next = current.segmentIndex + 1;

		switch( token ) {
		case SelectQueryParser.WildcardArrayContext t ->
			current.node.children().forEach( ( n, a ) -> pushArray( stack, a, path( current.path, n ), next ) );
		case SelectQueryParser.WildcardContext t ->
			current.node.children().forEach( ( n, a ) -> pushFirst( stack, a, path( current.path, n ), next ) );
		case SelectQueryParser.FieldArrayContext t -> {
			String name = t.ID().getText();
			if( current.node.hasChildren( name ) )
				pushArray( stack, current.node.getChildren( name ), path( current.path, name ), next );
		}
		case SelectQueryParser.FieldContext t -> {
			String name = t.ID().getText();
			if( current.node.hasChildren( name ) )
				pushFirst( stack, current.node.getChildren( name ), path( current.path, name ), next );
		}
		default -> throw new IllegalStateException( "Unknown token type" );
		}
	}

	private static void descendant( PathNode current, String fieldName, boolean allElements, Deque< PathNode > stack ) {
		int next = current.segmentIndex + 1;

		// Check current node
		if( current.node.hasChildren( fieldName ) ) {
			ValueVector arr = current.node.getChildren( fieldName );
			String p = path( current.path, fieldName );
			if( allElements ) {
				pushArray( stack, arr, p, next );
			} else {
				pushFirst( stack, arr, p, next );
			}
		}

		// Continue searching in children
		current.node.children().forEach( ( n, a ) -> {
			if( a.size() == 1 ) {
				stack.push( new PathNode( a.first(), path( current.path, n ), current.segmentIndex ) );
			} else {
				for( int i = 0; i < a.size(); i++ ) {
					stack.push(
						new PathNode( a.get( i ), path( current.path, n ) + "[" + i + "]", current.segmentIndex ) );
				}
			}
		} );
	}

	private static void pushArray( Deque< PathNode > stack, ValueVector arr, String basePath, int next ) {
		for( int i = 0; i < arr.size(); i++ )
			stack.push( new PathNode( arr.get( i ), basePath + "[" + i + "]", next ) );
	}

	private static void pushFirst( Deque< PathNode > stack, ValueVector arr, String p, int next ) {
		if( !arr.isEmpty() )
			stack.push( new PathNode( arr.first(), p, next ) );
	}

	private static String path( String curr, String child ) {
		return curr.isEmpty() ? child : curr + "." + child;
	}

	private record PathNode(Value node, String path, int segmentIndex) {
	}

	// ========== WHERE EVALUATION ==========

	private static class WhereEvaluator {
		private final SelectQueryParser.WhereClauseContext whereTree;

		WhereEvaluator( SelectQueryParser.WhereClauseContext whereTree ) {
			this.whereTree = whereTree;
		}

		boolean test( Value node ) {
			// OR: at least one clause must be true
			return whereTree.orExpr().andExpr().stream().anyMatch( c -> evalAnd( node, c ) );
		}

		// AND: all clauses must be true
		private boolean evalAnd( Value node, SelectQueryParser.AndExprContext ctx ) {
			return ctx.notExpr().stream().allMatch( c -> evalNot( node, c ) );
		}

		// NOT: negate expression
		private boolean evalNot( Value node, SelectQueryParser.NotExprContext ctx ) {
			return switch( ctx ) {
			case SelectQueryParser.NotExpressionContext c -> !evalNot( node, c.notExpr() );
			case SelectQueryParser.PrimaryExpressionContext c -> evalPrimary( node, c.primary() );
			default -> throw new IllegalStateException();
			};
		}

		// Parentheses or condition
		private boolean evalPrimary( Value node, SelectQueryParser.PrimaryContext ctx ) {
			return switch( ctx ) {
			case SelectQueryParser.ParenExpressionContext c ->
				c.orExpr().andExpr().stream().anyMatch( a -> evalAnd( node, a ) );
			case SelectQueryParser.ConditionExpressionContext c -> evalCond( node, c.condition() );
			default -> throw new IllegalStateException();
			};
		}

		// Leaf condition
		private boolean evalCond( Value node, SelectQueryParser.ConditionContext ctx ) {
			return switch( ctx ) {
			case SelectQueryParser.NodeValueContext c ->
				eq( node, c.value().getText() );
			case SelectQueryParser.PathExistsContext c ->
				evalPathExists( node, c.wherePath() );
			case SelectQueryParser.PathMatchContext c ->
				evalPath( node, c.wherePath(), c.value().getText() );
			default -> throw new IllegalStateException();
			};
		}

		// Check if path exists (e.g., .b.c in .)
		private boolean evalPathExists( Value node, SelectQueryParser.WherePathContext pathCtx ) {
			return walkDirectPath( node, pathCtx.wherePathSegment() ) != null;
		}

		// Walk direct path and return final node, or null if doesn't exist
		private Value walkDirectPath( Value node, List< SelectQueryParser.WherePathSegmentContext > segs ) {
			if( segs.isEmpty() )
				return null;

			Value current = node;
			for( SelectQueryParser.WherePathSegmentContext seg : segs ) {
				if( seg instanceof SelectQueryParser.WhereDirectSegmentContext direct ) {
					String fieldName = direct.ID().getText();
					if( !current.hasChildren( fieldName ) )
						return null;
					ValueVector arr = current.getChildren( fieldName );
					if( arr.isEmpty() )
						return null;
					current = arr.first();
				} else {
					return null; // Descendant patterns not supported in direct paths
				}
			}
			return current;
		}

		// Evaluate WHERE path conditions: .field = value, ..field = value, ..field[*] = value
		private boolean evalPath( Value node, SelectQueryParser.WherePathContext pathCtx, String expected ) {
			List< SelectQueryParser.WherePathSegmentContext > segs = pathCtx.wherePathSegment();
			if( segs.isEmpty() )
				return false;

			SelectQueryParser.WherePathSegmentContext first = segs.get( 0 );

			return switch( first ) {
			case SelectQueryParser.WhereDescendantArraySegmentContext ctx ->
				searchDesc( node, ctx.ID().getText(), expected, true );
			case SelectQueryParser.WhereDescendantSegmentContext ctx ->
				searchDesc( node, ctx.ID().getText(), expected, false );
			case SelectQueryParser.WhereDirectSegmentContext ctx ->
				walkDirectPath( node, segs ) instanceof Value v && eq( v, expected );
			default -> throw new IllegalStateException();
			};
		}

		// Recursively search descendants for field with matching value
		private boolean searchDesc( Value node, String fieldName, String expected, boolean allElements ) {
			// Check current node
			if( node.hasChildren( fieldName ) ) {
				ValueVector arr = node.getChildren( fieldName );
				if( allElements ) {
					for( int i = 0; i < arr.size(); i++ )
						if( eq( arr.get( i ), expected ) )
							return true;
				} else {
					if( !arr.isEmpty() && eq( arr.first(), expected ) )
						return true;
				}
			}

			// Recursively search children
			for( ValueVector arr : node.children().values() ) {
				if( !arr.isEmpty() && searchDesc( arr.first(), fieldName, expected, allElements ) )
					return true;
			}

			return false;
		}

		private boolean eq( Value v, String exp ) {
			try {
				return (v.isInt() && v.intValue() == Integer.parseInt( exp )) ||
					(v.isString() && v.strValue().equals( exp )) ||
					(v.isBool() && v.boolValue() == Boolean.parseBoolean( exp ));
			} catch( NumberFormatException e ) {
				return false;
			}
		}
	}
}
