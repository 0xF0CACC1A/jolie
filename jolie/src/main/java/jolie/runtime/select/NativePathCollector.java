package jolie.runtime.select;

import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import java.util.ArrayList;
import java.util.List;

/**
 * Native path collector for SELECT operations without ANTLR dependency. Collects paths from a Value
 * tree based on wildcard depth or recursive field lookup.
 */
public class NativePathCollector {

	/**
	 * Collect paths from a value tree based on wildcard depth.
	 *
	 * @param vec ValueVector to traverse
	 * @param rootPath Base path (e.g., "tree")
	 * @param wildcardDepth How many wildcard levels: 0=root only, 1=children (.*), 2=grandchildren
	 *        (.*.*)
	 * @return List of paths at the specified depth
	 */
	public static List< String > collectPaths( ValueVector vec, String rootPath, int wildcardDepth ) {
		List< String > paths = new ArrayList<>();

		if( wildcardDepth == 0 ) {
			// No wildcard: just return the root path
			paths.add( rootPath );
		} else {
			// Wildcard: traverse to specified depth
			collectPathsRecursive( vec.first(), rootPath, wildcardDepth, paths );
		}

		return paths;
	}

	/**
	 * Collect paths from a value tree using recursive descent to find all occurrences of a specific
	 * field.
	 *
	 * @param vec ValueVector to traverse
	 * @param rootPath Base path (e.g., "tree")
	 * @param targetField Field name to search for recursively (e.g., "value")
	 * @return List of all paths ending with the target field
	 */
	public static List< String > collectPathsRecursive( ValueVector vec, String rootPath, String targetField ) {
		List< String > paths = new ArrayList<>();
		collectPathsRecursiveField( vec.first(), rootPath, targetField, paths );
		return paths;
	}

	private static void collectPathsRecursive( Value node, String currentPath, int remainingDepth,
		List< String > paths ) {
		if( remainingDepth == 0 ) {
			// Reached target depth, collect this path
			paths.add( currentPath );
			return;
		}

		// Traverse children - using children() is safe, it returns existing children only (no vivification)
		node.children().forEach( ( fieldName, childVector ) -> {
			String childPath = currentPath.isEmpty() ? fieldName : currentPath + "." + fieldName;

			// For now, only handle first element in vector (childVector.first())
			// This matches the existing behavior of var.*
			if( !childVector.isEmpty() ) {
				collectPathsRecursive( childVector.first(), childPath, remainingDepth - 1, paths );
			}
		} );
	}

	private static void collectPathsRecursiveField( Value node, String currentPath, String targetField,
		List< String > paths ) {
		// Stack-based iterative DFS to avoid recursion
		java.util.Stack< java.util.Map.Entry< Value, String > > stack = new java.util.Stack<>();
		stack.push( new java.util.AbstractMap.SimpleEntry<>( node, currentPath ) );

		while( !stack.isEmpty() ) {
			java.util.Map.Entry< Value, String > entry = stack.pop();
			Value current = entry.getKey();
			String path = entry.getValue();

			// Check all children of current node
			current.children().forEach( ( fieldName, childVector ) -> {
				if( !childVector.isEmpty() ) {
					Value child = childVector.first();
					String childPath = path.isEmpty() ? fieldName : path + "." + fieldName;

					// If this field matches target, add its path
					if( fieldName.equals( targetField ) ) {
						paths.add( childPath );
					}

					// Push child onto stack to continue searching
					stack.push( new java.util.AbstractMap.SimpleEntry<>( child, childPath ) );
				}
			} );
		}
	}
}
