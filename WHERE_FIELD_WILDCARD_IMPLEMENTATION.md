# Field Wildcard in WHERE Clause (`$.*`) Implementation

## Summary

Successfully implemented support for field wildcards in PATHS WHERE expressions, enabling existential quantification over all child fields.

## New Syntax Supported

### Basic: `$.*`
```jolie
data.a = 10;
data.b = 20;
data.c = 5;

res << paths data where $.* == 5;
// Returns: data (because data.c == 5)
```

### Multi-level: `$.*.*`
```jolie
root.x.alpha = 100;
root.x.beta = 50;
root.y.gamma = 200;

res << paths root where $.*.* > 175;
// Returns: root (because root.y.gamma > 175)
```

### Combined with field access: `$.*.value`
```jolie
data.a.value = 100;
data.b.value = 50;
data.c.value = 200;

res << paths data where $.*.value > 150;
// Returns: data (because data.c.value > 150)
```

### With nested paths: `paths items[*] where $.*  > 100`
```jolie
store.items[0].price = 50;
store.items[1].price = 150;
store.items[2].price = 75;

res << paths store.items[*] where $.* > 100;
// Returns: store.items[1]
```

## Implementation Details

### Files Modified

1. **CurrentValueNode.java** (AST) - Added field wildcard support to FieldPathComponent
2. **OLParser.java** - Extended WHERE expression parsing to recognize `*` after DOT
3. **CurrentValueExpression.java** (Runtime) - Added field wildcard evaluation logic
4. **CompareCondition.java** - Updated to detect and route field wildcards
5. **OOITBuilder.java** - Fixed AST-to-runtime conversion for field wildcards
6. **run_native_tests.py** - Added field wildcard tests

### Design Strategy

**Existential Quantification Semantic:**
- `$.* == value` means "ANY child field equals value"
- Matches array wildcard semantic for consistency
- Short-circuit evaluation: returns true on first match

**Integration with Existing Architecture:**
- Reuses `FieldPathComponent` structure
- Added `hasFieldWildcard` boolean flag
- Minimal changes to existing code paths

### Algorithm

1. **Parser**: Recognizes `*` token after DOT in $ expressions
2. **AST**: Stores `FieldPathComponent` with `hasFieldWildcard=true`
3. **Runtime**:
   - `CompareCondition` detects field wildcards via `hasFieldWildcards()`
   - Routes to `evaluateArrayWildcardComparison()` (handles both array and field wildcards)
   - `checkPathWithWildcard()` iterates over all child fields
   - For each field, evaluates the remaining path
   - Returns true if ANY field satisfies the condition

### Evaluation Flow

For `$.* == 5` with `{a: 10, b: 20, c: 5}`:

```
checkPathWithWildcard(current={a:10, b:20, c:5}, path=[*], index=0)
  component = FieldPathComponent(hasFieldWildcard=true)

  Iterate over children:
    - field "a", value 10
      → checkPathWithWildcard(10, [], 1)
      → operator.test(10, 5) → false

    - field "b", value 20
      → checkPathWithWildcard(20, [], 1)
      → operator.test(20, 5) → false

    - field "c", value 5
      → checkPathWithWildcard(5, [], 1)
      → operator.test(5, 5) → true ✓

  Return true (found match)
```

### Vivification Prevention

- Iterates over existing `current.children()` map
- No path creation during traversal
- Gracefully handles non-existent fields (skips them)

## Test Coverage

All tests passing:

### Basic Tests
- `test_where_field_wildcard_basic.ol` - Simple `$.* == value`
- `test_where_field_wildcard_greater_than.ol` - Comparison operator `$.* > value`
- `test_where_field_wildcard_string.ol` - String comparison
- `test_where_field_wildcard_negation.ol` - NOT operator

### Multi-level Tests
- `test_where_field_wildcard_multi_level.ol` - `$.*.*` pattern
- `test_where_field_wildcard_deep.ol` - `$.*.*.value` pattern

### Combined Tests
- `test_where_field_wildcard_with_field.ol` - `$.*.field` pattern
- `test_where_field_wildcard_nested.ol` - With array wildcards in path
- `test_where_field_wildcard_boolean_and.ol` - Boolean operators

## Examples

### E-commerce Price Filter
```jolie
product.regular_price = 100;
product.sale_price = 75;
product.member_price = 60;

res << paths product where $.* < 80;
// Returns: product (sale_price and member_price match)
```

### Configuration Validation
```jolie
config.timeout = 5000;
config.retries = 3;
config.max_connections = 100;

res << paths config where $.* > 50;
// Returns: config (timeout and max_connections match)
```

### Nested Structure
```jolie
users.admin.status = "active";
users.admin.level = 5;
users.guest.status = "inactive";
users.guest.level = 1;

res << paths users.* where $.level > 3;
// Returns: users.admin
```

### Boolean Combinations
```jolie
item.min = 10;
item.max = 20;
item.value = 15;

res << paths item where $.* > 10 && $.* < 20;
// Returns: item (both value and max satisfy the condition)
```

## Field + Array Wildcard Combination: `$.*[*]`

### Basic Syntax
```jolie
data.x[0] = 5;
data.x[1] = 15;
data.y[0] = 25;
data.y[1] = 35;

res << paths data where $.*[*] > 20;
// Returns: data (because data.y[0] and data.y[1] > 20)
```

### Multi-level: `$.*.items[*].price`
```jolie
data.store1.items[0].price = 10;
data.store1.items[1].price = 50;
data.store2.items[0].price = 100;

res << paths data where $.*.items[*].price > 75;
// Returns: data (because data.store2.items[0].price > 75)
```

### Deep Nesting: `$.*.*[*]`
```jolie
root.branch1.leaves[0] = 10;
root.branch1.leaves[1] = 20;
root.branch2.leaves[0] = 50;

res << paths root where $.*.*[*] > 25;
// Returns: root (because root.branch2.leaves[0] > 25)
```

### With Boolean Operators
```jolie
data.a[0] = 5;
data.a[1] = 15;
data.a[2] = 25;

res << paths data where $.*[*] > 20 && $.*[*] < 10;
// Returns: data (has elements both > 20 AND < 10)
```

### Combined with Path Wildcards
```jolie
root[0].a[0] = 10;
root[1].a[0] = 200;

res << paths root[*] where $.*[*] > 150;
// Returns: root[1] (root[1].a[0] > 150)
```

## Implementation Notes

The `$.*[*]` combination works through nested iteration:
1. **Field wildcard (`.*`)**: Iterates over all child fields using `children()` map
2. **Array wildcard (`[*]`)**: For each field, iterates over array elements
3. **Existential semantics**: Returns true if ANY field's ANY element matches

The implementation is fully iterative (no recursion) and prevents vivification by only traversing existing structure.

### Already Working (Not Changed)

- `$.field[*]` - Array wildcard on specific field (already supported)
- `$..field` - Recursive field descent (already supported)
- Boolean operators with multiple `$` (already supported)

## Performance

- **Iterative Only**: Uses `children()` map iterator, no recursion risk
- **Short-Circuit**: Returns true on first matching field
- **No Vivification**: Only iterates existing fields
- **Efficient for Small Structures**: O(n) where n = number of child fields

## Future Work

Potential extensions:
- `$.*[*].*` - Continue traversing after array elements (e.g., `$.*[*].field`)
- Universal quantification (`$.all.*` or similar syntax for "ALL must match" instead of "ANY must match")

## Integration with Existing Features

### Works With Array Wildcards in Path
```jolie
res << paths data.items[*] where $.* > 100;
// Combines path array wildcard with WHERE field wildcard
```

### Works With Recursive Fields in Path
```jolie
res << paths tree..items where $.* == "active";
// Combines path recursive descent with WHERE field wildcard
```

### Works With Boolean Operators
```jolie
res << paths data where $.* > 10 && $.* < 20;
res << paths data where $.* == "red" || $.* == "blue";
res << paths data where !($.* == 0);
// All boolean operators supported
```

## Architecture Summary

```
WHERE Expression: $.* == 5
        ↓
OLParser parses DOLLAR DOT ASTERISK
        ↓
Creates CurrentValueNode with:
  fieldPathComponents = [FieldPathComponent(hasFieldWildcard=true)]
        ↓
OOITBuilder converts to runtime:
  CurrentValueExpression with:
    fieldPathComponents = [FieldPathComponent(hasFieldWildcard=true)]
        ↓
CompareCondition.evaluate() detects hasFieldWildcards()
        ↓
Calls evaluateArrayWildcardComparison()
        ↓
checkPathWithWildcard() iterates current.children()
        ↓
For each field:
  - Navigate to field value
  - Continue with remaining path
  - Return true if operator.test() succeeds
        ↓
Returns boolean result (existential quantification)
```
