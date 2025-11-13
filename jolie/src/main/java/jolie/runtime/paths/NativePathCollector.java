package jolie.runtime.paths;

import jolie.runtime.Value;
import jolie.runtime.ValueVector;
import java.util.ArrayList;
import java.util.List;

/**
 * Native path collector for PATHS operations without ANTLR dependency. Collects paths from a Value
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

	/**
	 * Collect all array element paths for a given field path using array wildcard [*].
	 *
	 * @param vec ValueVector to start from (the base variable's vector)
	 * @param rootPath Base path (e.g., "tree" or "data")
	 * @param fieldPath Field path to array: "" (empty) for base array (data[*]), "items" for single
	 *        field (tree.items[*]), "field.subfield" for nested (tree.field.subfield[*])
	 * @return List of paths like "data[0]", "data[1]" or "tree.items[0]", "tree.items[1]"
	 */
	public static List< String > collectArrayPaths( ValueVector vec, String rootPath, String fieldPath ) {
		List< String > paths = new ArrayList<>();

		// Determine which ValueVector to expand based on fieldPath
		ValueVector arrayVector = null;
		String fullPathPrefix;

		if( fieldPath == null || fieldPath.isEmpty() ) {
			// Base variable array: data[*]
			// The vec parameter is already the array we want to expand
			arrayVector = vec;
			fullPathPrefix = rootPath;
		} else {
			// Nested field array: tree.items[*] or tree.field.subfield[*]
			// Navigate to the field iteratively (no recursion, no vivification)
			Value current = vec.first();
			String[] fieldParts = fieldPath.split( "\\." );

			for( String fieldName : fieldParts ) {
				// Check existence before accessing (avoid vivification)
				if( !current.hasChildren( fieldName ) ) {
					// Field doesn't exist, return empty list
					return paths;
				}
				// For all but the last field part, navigate deeper into the structure
				if( fieldName.equals( fieldParts[ fieldParts.length - 1 ] ) ) {
					// This is the last field - we want its ValueVector, not its first Value
					// Use getChildren() to get the ValueVector at this field
					arrayVector = current.getChildren( fieldName );
				} else {
					// Not the last field, continue navigation
					current = current.getFirstChild( fieldName );
				}
			}

			// Build the full path prefix: rootPath + "." + fieldPath
			fullPathPrefix = rootPath + "." + fieldPath;
		}

		// Now iterate through all array indices - iterative, no recursion
		// arrayVector will be non-null here if we got this far
		if( arrayVector != null ) {
			for( int i = 0; i < arrayVector.size(); i++ ) {
				String fullPath = fullPathPrefix + "[" + i + "]";
				paths.add( fullPath );
			}
		}

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
