# Wildcard + Array Combination (`.*[*]`) Implementation

## Summary

Successfully implemented support for combining wildcard field selection with array wildcard enumeration in the PATHS primitive.

## New Syntax Supported

### Basic: `var.*[*]`
```jolie
tree.a[0] = 5;
tree.a[1] = 15;
tree.b[0] = 25;

res << paths tree.*[*] where $ > 10;
// Returns: tree.a[1], tree.b[0]
```

### Multi-level: `var.*.*[*]`
```jolie
data.x.alpha[0] = 5;
data.x.alpha[1] = 10;
data.y.beta[0] = 15;

res << paths data.*.*[*] where $ > 8;
// Returns: data.x.alpha[1], data.y.beta[0]
```

### Very deep: `var.*.*.*[*]`
```jolie
root.level1.level2.items[0] = 100;

res << paths root.*.*.*[*] where $ > 50;
```

## Implementation Details

### Files Modified (5)

1. **NativePathCollector.java** (+88 lines)
   - Added `collectWildcardArrayPaths()` method
   - Added `navigateToPath()` helper method
   - Fully iterative with vivification prevention

2. **OLParser.java** (+9 lines in 2 locations)
   - After wildcard parsing, checks for `[*]`
   - Sets `arrayWildcardPath = ""` to signal combination
   - Both statement and expression variants updated

3. **PathsProcess.java** (+4 lines)
   - Added first condition: `arrayWildcardPath != null && wildcardDepth > 0`
   - Calls new collector method

4. **PathsExpression.java** (+4 lines)
   - Same runtime logic as PathsProcess

5. **ARRAY_WILDCARD_IMPLEMENTATION.md**
   - Updated "Out of Scope" → "Now Supported!"
   - Added examples and implementation notes

### Design Strategy

**Key Insight**: Reuse `arrayWildcardPath` field with dual semantics:
- When `wildcardDepth = 0`: holds field path (e.g., `"items"` for `tree.items[*]`)
- When `wildcardDepth > 0`: empty string `""` signals wildcard+array combo

**No new fields needed in PathSpecNode!**

### Algorithm

1. **Parser**: Recognizes `.*[*]` token sequence
2. **AST**: Stores `wildcardDepth=1` + `arrayWildcardPath=""`
3. **Runtime**:
   - Calls `collectPaths(vec, root, depth)` → gets wildcard paths
   - For each path, enumerates array elements at that path
   - Returns combined list

### Vivification Prevention

- Uses `hasChildren()` before accessing paths
- Gracefully handles non-existent paths (returns empty list)
- Handles non-array fields (skips them)

## Test Coverage

**51 total tests passing**, including:

### Basic Tests (6)
- `test_wildcard_array_basic.ol` - Simple `.*[*]`
- `test_wildcard_array_multi_level.ol` - `.*.*[*]`
- `test_wildcard_array_objects.ol` - Objects with field access
- `test_wildcard_array_boolean.ol` - Boolean operators
- `test_wildcard_array_empty.ol` - Vivification check
- `test_wildcard_array_string.ol` - String comparisons

### Complex Tests (6)
- `test_wildcard_array_complex_nesting.ol` - Nested objects with arrays
- `test_wildcard_array_mixed_types.ol` - Mixed scalars/arrays
- `test_wildcard_array_deep_multilevel.ol` - `.*.*.*[*]` (3 levels)
- `test_wildcard_array_empty_arrays.ol` - Edge cases
- `test_wildcard_array_negation.ol` - Negation operator
- `test_wildcard_array_with_field_access.ol` - Deep field access

## Examples

### E-commerce Inventory
```jolie
store.electronics[0].price = 1200;
store.electronics[1].price = 800;
store.books[0].price = 25;

res << paths store.*[*] where $.price > 100;
// Returns: store.electronics[0], store.electronics[1]
```

### Complex Nesting
```jolie
inventory.warehouse1[0].item.quantity = 50;
inventory.warehouse1[1].item.quantity = 150;
inventory.warehouse2[0].item.quantity = 200;

res << paths inventory.*[*] where $.item.quantity > 100;
// Returns: inventory.warehouse1[1], inventory.warehouse2[0]
```

### Boolean Logic
```jolie
data.x[0] = 5;
data.x[1] = 15;
data.y[0] = 20;

res << paths data.*[*] where $ > 10 && $ < 20;
// Returns: data.x[1]
```

## Limitations

Still **out of scope**:
- `var[*].*` - Children of array elements (different traversal pattern)
- `var..field[*]` - Recursive + array (complexity)

## Performance

- **Iterative only**: No recursion, no stack overflow risk
- **Single pass**: Collects wildcards once, then expands arrays
- **No vivification**: Safe path traversal with existence checks

## Future Work

Potential extensions:
- `var[*].*` - Enumerate children of each array element
- `var..field[*]` - Recursive field search with array wildcard
- `var.*[*].*` - Continue traversing after array elements
