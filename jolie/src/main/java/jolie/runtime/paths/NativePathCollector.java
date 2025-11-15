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
	 * Collect all array element paths for a recursively found field with array wildcard. This handles
	 * syntax like var..field[*] where we find all occurrences of 'field' recursively, and for each one
	 * that is an array, enumerate all its elements.
	 *
	 * @param vec ValueVector to traverse
	 * @param rootPath Base path (e.g., "data")
	 * @param targetField Field name to search for recursively (e.g., "name")
	 * @return List of paths like "data.name[0]", "data.name[1]", "data.nested.name[0]"
	 */
	public static List< String > collectRecursiveArrayPaths( ValueVector vec, String rootPath,
		String targetField ) {
		List< String > paths = new ArrayList<>();
		collectRecursiveArrayPathsHelper( vec.first(), rootPath, targetField, paths );
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

	/**
	 * Collect array element paths by combining wildcard depth traversal with array expansion. This
	 * handles syntax like var.*[*] (all arrays in children) or var.*.*[*] (all arrays in
	 * grandchildren).
	 *
	 * @param vec ValueVector to start from (the base variable's vector)
	 * @param rootPath Base path (e.g., "tree")
	 * @param wildcardDepth How many wildcard levels before array expansion
	 * @return List of paths like "tree.a[0]", "tree.a[1]", "tree.b[0]", "tree.b[1]"
	 */
	public static List< String > collectWildcardArrayPaths( ValueVector vec, String rootPath,
		int wildcardDepth ) {
		List< String > paths = new ArrayList<>();

		// Step 1: Get all paths at the wildcard depth using existing method
		// For tree.*, this gives: ["tree.a", "tree.b", "tree.c"]
		// For tree.*.*, this gives: ["tree.a.x", "tree.a.y", "tree.b.z"]
		List< String > wildcardPaths = collectPaths( vec, rootPath, wildcardDepth );

		// Step 2: For each wildcard path, enumerate array elements if it's an array
		// This is fully iterative - no recursion
		for( String wildcardPath : wildcardPaths ) {
			// Navigate to the value at this path iteratively (with vivification prevention)
			ValueVector targetVector = navigateToPath( vec, wildcardPath, rootPath );

			// If navigation succeeded (path exists), enumerate array indices
			if( targetVector != null && targetVector.size() > 0 ) {
				// The targetVector is the array we want to expand
				for( int i = 0; i < targetVector.size(); i++ ) {
					String arrayPath = wildcardPath + "[" + i + "]";
					paths.add( arrayPath );
				}
			}
		}

		return paths;
	}

	/**
	 * Collect paths by first expanding array elements, then collecting wildcard paths for each element.
	 * This handles syntax like var[*].* (all children of all array elements) or var[*].*.* (all
	 * grandchildren of all array elements).
	 *
	 * @param vec ValueVector to start from (the base variable's array vector)
	 * @param rootPath Base path (e.g., "data")
	 * @param wildcardDepth How many wildcard levels after array expansion
	 * @return List of paths like "data[0].x", "data[0].y", "data[1].x", "data[1].y"
	 */
	public static List< String > collectArrayWildcardPaths( ValueVector vec, String rootPath,
		int wildcardDepth ) {
		List< String > paths = new ArrayList<>();

		// Step 1: Iterate through all array elements of the base variable
		// This is fully iterative - no recursion
		for( int i = 0; i < vec.size(); i++ ) {
			String arrayElementPath = rootPath + "[" + i + "]";

			// Step 2: For each array element, collect paths at wildcard depth
			if( wildcardDepth == 0 ) {
				// No wildcard after array: just return array element paths
				paths.add( arrayElementPath );
			} else {
				// Wildcard after array: collect paths from this array element
				Value arrayElement = vec.get( i );

				// Collect paths at wildcard depth from this element
				collectPathsRecursive( arrayElement, arrayElementPath, wildcardDepth, paths );
			}
		}

		return paths;
	}

	/**
	 * Navigate to a specific path iteratively, with vivification prevention. Returns null if the path
	 * doesn't exist.
	 *
	 * @param baseVec Starting ValueVector
	 * @param fullPath Full path to navigate to (e.g., "tree.a" or "tree.a.x")
	 * @param rootPath Root portion of the path (e.g., "tree")
	 * @return ValueVector at the target path, or null if path doesn't exist
	 */
	private static ValueVector navigateToPath( ValueVector baseVec, String fullPath, String rootPath ) {
		// If fullPath equals rootPath, we're already at the target
		if( fullPath.equals( rootPath ) ) {
			return baseVec;
		}

		// Extract the relative path after rootPath
		// fullPath = "tree.a.x", rootPath = "tree" → relativePath = "a.x"
		String relativePath;
		if( fullPath.startsWith( rootPath + "." ) ) {
			relativePath = fullPath.substring( rootPath.length() + 1 );
		} else {
			// Path doesn't start with rootPath - shouldn't happen, but handle gracefully
			return null;
		}

		// Navigate iteratively through the path parts
		Value current = baseVec.first();
		String[] pathParts = relativePath.split( "\\." );

		for( String part : pathParts ) {
			// Check existence before accessing (vivification prevention)
			if( !current.hasChildren( part ) ) {
				// Path doesn't exist
				return null;
			}
			// For the last part, we want to return the ValueVector, not navigate into it
			if( part.equals( pathParts[ pathParts.length - 1 ] ) ) {
				// This is the target - return its ValueVector
				return current.getChildren( part );
			} else {
				// Navigate to next level
				current = current.getFirstChild( part );
			}
		}

		// Shouldn't reach here, but return null if we do
		return null;
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

	private static void collectRecursiveArrayPathsHelper( Value node, String currentPath, String targetField,
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

					// If this field matches target and is an array, enumerate all elements
					if( fieldName.equals( targetField ) ) {
						// Check if it's an array (ValueVector with multiple indices)
						for( int i = 0; i < childVector.size(); i++ ) {
							String arrayElementPath = childPath + "[" + i + "]";
							paths.add( arrayElementPath );
						}
					}

					// Push child onto stack to continue searching
					stack.push( new java.util.AbstractMap.SimpleEntry<>( child, childPath ) );
				}
			} );
		}
	}
}
