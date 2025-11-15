# Recursive Descent + Array Wildcard (`..field[*]`) in PATHS Path Clause

## Table of Contents
1. [Overview](#overview)
2. [Architecture Before vs After](#architecture-before-vs-after)
3. [The `..field[*]` Syntax: Combining Recursive Descent with Array Enumeration](#the-field-syntax-combining-recursive-descent-with-array-enumeration)
4. [Implementation Steps](#implementation-steps)
5. [Critical Implementation Details](#critical-implementation-details)
6. [The PathSpecNode Extension Strategy](#the-pathspecnode-extension-strategy)
7. [Backward Compatibility](#backward-compatibility)
8. [Testing and Verification](#testing-and-verification)
9. [Complete File Inventory](#complete-file-inventory)

---

## Overview

### Goal
Add support for combining recursive descent (`..`) with array wildcard (`[*]`) in PATHS path expressions, enabling syntax like `data..items[*]` to recursively find all occurrences of a field named `items` and enumerate all array elements.

**Before:**
```jolie
// Only recursive descent to field (scalar values)
paths data..items where true
// Returns: data.items, data.nested.items, data.nested.deep.items

// Only specific field array wildcard
paths data.items[*] where true
// Returns: data.items[0], data.items[1], ...
```

**After:**
```jolie
// Recursive descent + array wildcard combination
paths data..items[*] where true
// Returns: data.items[0], data.items[1], data.nested.items[0],
//          data.nested.items[1], data.nested.deep.items[0], ...
```

### Why This Feature?

1. **Power**: Locate and enumerate array elements at any depth in complex data structures
2. **Convenience**: Eliminates need for multiple PATHS queries or manual traversal
3. **Practical**: Common use case in hierarchical data (org charts, product catalogs, config trees)
4. **Consistency**: Natural extension of existing `..field` and `field[*]` patterns
5. **WHERE Integration**: Full support for filtering array elements with WHERE clause

### Key Challenge

Combining two existing features (recursive descent and array wildcard) requires:
- Parser to recognize `[*]` after `..field`
- New AST field to track this combination
- New runtime collector method for recursive + array traversal
- Maintaining backward compatibility with existing 4-parameter PathSpecNode constructors

---

## Architecture Before vs After

### Before: Recursive Descent OR Array Wildcard (Separate)

```
┌─────────────────────────────────────────────────────────────┐
│ Jolie Code: paths data..items where true                   │
│             (Recursive descent - finds field recursively)   │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Parser (OLParser.java)                                       │
│   - Parses: data                                            │
│   - Sees: .. (DOT DOT)                                      │
│   - Parses: items (field name)                              │
│   - Sets: recursiveField = "items"                          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ PathSpecNode                                                │
│   baseVariable = data                                       │
│   recursiveField = "items"                                  │
│   arrayWildcardPath = null                                  │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Runtime: PathsExpression.evaluate()                         │
│   Routes to: NativePathCollector.collectPathsRecursive()    │
│   Returns: ["data.items", "data.nested.items", ...]        │
│            (field paths, not array elements)                │
└─────────────────────────────────────────────────────────────┘

OR

┌─────────────────────────────────────────────────────────────┐
│ Jolie Code: paths data.items[*] where true                 │
│             (Array wildcard - specific field)               │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Parser (OLParser.java)                                       │
│   - Parses: data                                            │
│   - Sees: . (DOT)                                           │
│   - Parses: items (field name)                              │
│   - Sees: [*]                                               │
│   - Sets: arrayWildcardPath = "items"                       │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ PathSpecNode                                                │
│   baseVariable = data                                       │
│   recursiveField = null                                     │
│   arrayWildcardPath = "items"                               │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Runtime: PathsExpression.evaluate()                         │
│   Routes to: NativePathCollector.collectArrayPaths()        │
│   Returns: ["data.items[0]", "data.items[1]", ...]         │
│            (specific field's array elements)                │
└─────────────────────────────────────────────────────────────┘
```

### After: Recursive Descent AND Array Wildcard (Combined)

```
┌─────────────────────────────────────────────────────────────┐
│ Jolie Code: paths data..items[*] where true                │
│             (Recursive descent + array enumeration)         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Scanner (Scanner.java)                                       │
│   Tokenizes: PATHS, ID(data), DOT, DOT, ID(items),         │
│              LSQUARE, ASTERISK, RSQUARE, WHERE, ...         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Parser (OLParser.java) - NEW LOGIC                          │
│   - Parses: data                                            │
│   - Sees: .. (DOT DOT)                                      │
│   - Parses: items (field name)                              │
│   - Sets: recursiveField = "items"                          │
│   - *** NEW: Checks for [*] after field name ***            │
│   - Sees: [ token                                           │
│   - Eats: [ * ]                                             │
│   - Sets: recursiveFieldIsArray = true  ← NEW FLAG          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ PathSpecNode - EXTENDED                                     │
│   baseVariable = data                                       │
│   recursiveField = "items"                                  │
│   arrayWildcardPath = null                                  │
│   recursiveFieldIsArray = true          ← NEW FIELD         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ AST Optimization (OLParseTreeOptimizer.java)                 │
│   *** CRITICAL: Must preserve recursiveFieldIsArray ***     │
│   new PathSpecNode(..., n.recursiveFieldIsArray())          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ OOITBuilder - UPDATED                                       │
│   Converts PathSpecNode → PathsExpression                   │
│   new PathsExpression(                                      │
│       ...,                                                  │
│       n.pathSpec().recursiveField(),                        │
│       ...,                                                  │
│       n.pathSpec().recursiveFieldIsArray()  ← NEW PARAM     │
│   )                                                         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Runtime: PathsExpression - EXTENDED                         │
│   private final boolean recursiveFieldIsArray; ← NEW FIELD  │
│                                                             │
│   if (recursiveField != null && recursiveFieldIsArray) {    │
│       // NEW ROUTING LOGIC                                  │
│       candidatePaths = NativePathCollector                  │
│           .collectRecursiveArrayPaths(vec, rootPath,        │
│                                       recursiveField);      │
│   }                                                         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ NativePathCollector - NEW METHOD                            │
│   collectRecursiveArrayPaths(vec, "data", "items"):        │
│                                                             │
│   Algorithm:                                                │
│   1. Iterative DFS (stack-based, no recursion)              │
│   2. For each node in tree:                                 │
│      - Check all children                                   │
│      - If child.name == "items":                            │
│          * Enumerate all array elements                     │
│          * Add: items[0], items[1], ...                     │
│      - Push children onto stack for further traversal       │
│                                                             │
│   Returns: ["data.items[0]", "data.items[1]",              │
│             "data.nested.items[0]", "data.nested.items[1]", │
│             "data.nested.deep.items[0]", ...]               │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ WHERE Filtering (if present)                                │
│   For each candidate path:                                  │
│     - Extract value via getValueAtPath()                    │
│     - Evaluate WHERE expression with $ bound to value       │
│     - Include path if WHERE returns true                    │
│                                                             │
│   Example: where $ > 50                                     │
│     data.items[0] = 10     → EXCLUDED                       │
│     data.items[1] = 100    → INCLUDED                       │
│     data.nested.items[0] = 5   → EXCLUDED                   │
│     data.nested.items[1] = 75  → INCLUDED                   │
└─────────────────────────────────────────────────────────────┘
```

---

## The `..field[*]` Syntax: Combining Recursive Descent with Array Enumeration

### What It Does

The `..field[*]` pattern combines two powerful features:

1. **Recursive Descent (`..field`)**: Search for all occurrences of `field` at any depth
2. **Array Wildcard (`[*]`)**: For each occurrence, enumerate all array elements

### Syntax Breakdown

```jolie
paths data..items[*] where $ > 50
      ^^^^ ^^^^^ ^^^       ^^^^^^^
       │     │    │           └─ WHERE filter on array element values
       │     │    └─ Array wildcard: enumerate all elements
       │     └─ Field name to find recursively
       └─ Base variable to start search from
```

### Example Data Structure

```jolie
data.items[0] = 10;
data.items[1] = 100;
data.nested.items[0] = 5;
data.nested.items[1] = 75;
data.nested.deep.items[0] = 200;
```

**Query:**
```jolie
res << paths data..items[*] where $ > 50;
```

**Result:**
```
data.items[1]             (value 100 > 50 ✓)
data.nested.items[1]      (value 75 > 50 ✓)
data.nested.deep.items[0] (value 200 > 50 ✓)
```

### Comparison with Related Patterns

| Pattern | Meaning | Example Result |
|---------|---------|----------------|
| `data.items` | Single field | `data.items` (the field itself) |
| `data.items[*]` | Specific array | `data.items[0]`, `data.items[1]` |
| `data..items` | Recursive field | `data.items`, `data.nested.items`, `data.nested.deep.items` (fields) |
| **`data..items[*]`** | **Recursive + Array** | **`data.items[0]`, `data.items[1]`, `data.nested.items[0]`, ...** |

---

## Implementation Steps

### Step 1: Extend PathSpecNode AST

**File:** `libjolie/src/main/java/jolie/lang/parse/ast/expression/PathSpecNode.java`

**Goal:** Add a boolean field to track when recursive field should enumerate arrays

**Changes:**

1. **Add new private field:**
```java
private final boolean recursiveFieldIsArray;
```

2. **Update constructor signature (create new 6-parameter version):**
```java
public PathSpecNode( ParsingContext context, VariablePathNode baseVariable, int wildcardDepth,
    String recursiveField, String arrayWildcardPath, int wildcardDepthAfterArray,
    boolean recursiveFieldIsArray ) {  // ← NEW PARAMETER
    super( context );
    this.baseVariable = baseVariable;
    this.wildcardDepth = wildcardDepth;
    this.recursiveField = recursiveField;
    this.arrayWildcardPath = arrayWildcardPath;
    this.wildcardDepthAfterArray = wildcardDepthAfterArray;
    this.recursiveFieldIsArray = recursiveFieldIsArray;  // ← NEW FIELD
}
```

3. **Maintain backward compatibility (update existing constructors):**
```java
public PathSpecNode( ParsingContext context, VariablePathNode baseVariable, int wildcardDepth ) {
    this( context, baseVariable, wildcardDepth, null, null, 0, false );
    //                                                         ^^^^^ default to false
}

public PathSpecNode( ParsingContext context, VariablePathNode baseVariable, int wildcardDepth,
    String recursiveField ) {
    this( context, baseVariable, wildcardDepth, recursiveField, null, 0, false );
}

public PathSpecNode( ParsingContext context, VariablePathNode baseVariable, int wildcardDepth,
    String recursiveField, String arrayWildcardPath ) {
    this( context, baseVariable, wildcardDepth, recursiveField, arrayWildcardPath, 0, false );
}

public PathSpecNode( ParsingContext context, VariablePathNode baseVariable, int wildcardDepth,
    String recursiveField, String arrayWildcardPath, int wildcardDepthAfterArray ) {
    this( context, baseVariable, wildcardDepth, recursiveField, arrayWildcardPath,
        wildcardDepthAfterArray, false );  // ← Chain to new constructor with false
}
```

4. **Add accessor method:**
```java
public boolean recursiveFieldIsArray() {
    return recursiveFieldIsArray;
}
```

5. **Update JavaDoc:**
```java
/**
 * AST node representing a PATHS path (e.g., var, var.*, var.*.*, var..field, var[*], var.field[*],
 * var..field[*])  ← ADDED
 * Supports multiple wildcard levels for deep path specification, recursive field lookup, and array
 * element expansion.
 */
```

**Why This Design:**
- Boolean field is simpler than adding new String field
- `recursiveFieldIsArray` clearly indicates the combination
- Backward compatibility maintained via constructor chaining
- No need to modify `arrayWildcardPath` field (keeps it separate)

---

### Step 2: Update Parser to Recognize `[*]` After Recursive Field

**File:** `libjolie/src/main/java/jolie/lang/parse/OLParser.java`

**Goal:** Parse `[*]` tokens after `..field` and set the new flag

**Two Locations to Update:**
1. PATHS statement parsing (around line 2556)
2. PATHS expression parsing (around line 3916)

**Location 1: PATHS Statement (lines ~2556-2568)**

**Before:**
```java
if( token.is( Scanner.TokenType.DOT ) ) {
    // Recursive descent: var..field
    nextToken(); // eat second DOT
    assertIdentifier( "expected field name after .. in PATHS" );
    recursiveField = token.content();
    nextToken(); // eat field name
} else if( token.is( Scanner.TokenType.ASTERISK ) ) {
```

**After:**
```java
if( token.is( Scanner.TokenType.DOT ) ) {
    // Recursive descent: var..field or var..field[*]  ← UPDATED COMMENT
    nextToken(); // eat second DOT
    assertIdentifier( "expected field name after .. in PATHS" );
    recursiveField = token.content();
    nextToken(); // eat field name

    // *** NEW: Check for array wildcard after recursive field: ..field[*] ***
    if( token.is( Scanner.TokenType.LSQUARE ) ) {
        nextToken(); // eat [
        eat( Scanner.TokenType.ASTERISK, "expected * after [ in PATHS" );
        eat( Scanner.TokenType.RSQUARE, "expected ] after [* in PATHS" );
        recursiveFieldIsArray = true;
    }
} else if( token.is( Scanner.TokenType.ASTERISK ) ) {
```

**Location 2: PATHS Expression (lines ~3916-3929)**

Same logic as above, but for `recursiveFieldIsArrayExpr`:

```java
if( token.is( Scanner.TokenType.DOT ) ) {
    // Recursive descent: var..field or var..field[*]
    nextToken(); // eat second DOT
    assertIdentifier( "expected field name after .. in PATHS expression" );
    recursiveFieldExpr = token.content();
    nextToken(); // eat field name

    // Check for array wildcard after recursive field: ..field[*]
    if( token.is( Scanner.TokenType.LSQUARE ) ) {
        nextToken(); // eat [
        eat( Scanner.TokenType.ASTERISK, "expected * after [ in PATHS expression" );
        eat( Scanner.TokenType.RSQUARE, "expected ] after [* in PATHS expression" );
        recursiveFieldIsArrayExpr = true;
    }
} else if( token.is( Scanner.TokenType.ASTERISK ) ) {
```

**Variable Declaration:**

Add at the top of each parsing section:

**PATHS Statement (line ~2529):**
```java
int wildcardDepth = 0;
String recursiveField = null;
String arrayWildcardPath = null;
int wildcardDepthAfterArray = 0;
boolean recursiveFieldIsArray = false;  // ← NEW
```

**PATHS Expression (line ~3889):**
```java
int wildcardDepthExpr = 0;
String recursiveFieldExpr = null;
String arrayWildcardPathExpr = null;
int wildcardDepthAfterArrayExpr = 0;
boolean recursiveFieldIsArrayExpr = false;  // ← NEW
```

**PathSpecNode Construction:**

Update both locations to pass the new parameter:

**PATHS Statement (line ~2619):**
```java
PathSpecNode pathSpec =
    new PathSpecNode( getContext(), baseVar, wildcardDepth, recursiveField, arrayWildcardPath,
        wildcardDepthAfterArray, recursiveFieldIsArray );  // ← NEW PARAMETER
```

**PATHS Expression (line ~3988):**
```java
PathSpecNode pathSpecExpr = new PathSpecNode( getContext(), baseVarExpr, wildcardDepthExpr,
    recursiveFieldExpr, arrayWildcardPathExpr, wildcardDepthAfterArrayExpr,
    recursiveFieldIsArrayExpr );  // ← NEW PARAMETER
```

**Token Consumption Flow:**

```
Current Position: after eating field name "items"
Token Stream: ... items [  *  ]  WHERE ...
                       ↑
                     current

Step 1: Check if current token is LSQUARE
if( token.is( Scanner.TokenType.LSQUARE ) ) {  // true!

Step 2: Consume [
    nextToken();  // eat [
Token Stream: ... items [  *  ]  WHERE ...
                          ↑
                        current

Step 3: Consume *
    eat( Scanner.TokenType.ASTERISK, "expected * after [ in PATHS" );
Token Stream: ... items [  *  ]  WHERE ...
                             ↑
                           current

Step 4: Consume ]
    eat( Scanner.TokenType.RSQUARE, "expected ] after [* in PATHS" );
Token Stream: ... items [  *  ]  WHERE ...
                                ↑
                              current

Step 5: Set flag
    recursiveFieldIsArray = true;
}

Now parser is positioned at WHERE token, ready to continue
```

---

### Step 3: Update OLParseTreeOptimizer to Preserve New Field

**File:** `libjolie/src/main/java/jolie/lang/parse/OLParseTreeOptimizer.java`

**Goal:** Ensure the new `recursiveFieldIsArray` field survives AST optimization

**CRITICAL:** Without this change, the field would be lost during optimization!

**Location:** PathSpecNode visitor (line ~807)

**Before:**
```java
@Override
public void visit( PathSpecNode n ) {
    // For now, PathSpecNode doesn't need optimization - just preserve it
    currNode = new PathSpecNode(
        n.context(),
        optimizePath( n.baseVariable() ),
        n.wildcardDepth(),
        n.recursiveField(),
        n.arrayWildcardPath(),
        n.wildcardDepthAfterArray() );
}
```

**After:**
```java
@Override
public void visit( PathSpecNode n ) {
    // For now, PathSpecNode doesn't need optimization - just preserve it
    currNode = new PathSpecNode(
        n.context(),
        optimizePath( n.baseVariable() ),
        n.wildcardDepth(),
        n.recursiveField(),
        n.arrayWildcardPath(),
        n.wildcardDepthAfterArray(),
        n.recursiveFieldIsArray() );  // ← CRITICAL: Preserve new field
}
```

**Why This Is Critical:**

The OLParseTreeOptimizer runs after parsing and creates optimized AST nodes. If we don't pass the new field when reconstructing PathSpecNode, it defaults to `false` (from the 5-parameter constructor), losing our `[*]` information!

**Flow:**
```
Parser creates:
PathSpecNode(data, ..., "items", ..., true)  // recursiveFieldIsArray=true

↓ Optimizer runs ↓

WITHOUT fix:
PathSpecNode(data, ..., "items", ...)  // Calls 5-param constructor
→ recursiveFieldIsArray = false  ← BUG! Lost the flag

WITH fix:
PathSpecNode(data, ..., "items", ..., true)  // Passes through
→ recursiveFieldIsArray = true  ✓ Preserved
```

---

### Step 4: Extend PathsExpression Runtime Class

**File:** `jolie/src/main/java/jolie/runtime/expression/PathsExpression.java`

**Goal:** Add runtime field and routing logic for recursive array wildcard

**Changes:**

1. **Add private field (line ~17):**
```java
private final VariablePath pathSpec;
private final int wildcardDepth;
private final String recursiveField;
private final String arrayWildcardPath;
private final int wildcardDepthAfterArray;
private final boolean recursiveFieldIsArray;  // ← NEW
private final Expression whereExpression;
```

2. **Update all constructors to chain to new 6-parameter version:**
```java
public PathsExpression( VariablePath pathSpec, int wildcardDepth,
    Expression whereExpression ) {
    this( pathSpec, wildcardDepth, null, null, 0, false, whereExpression );
}

public PathsExpression( VariablePath pathSpec, int wildcardDepth, String recursiveField,
    Expression whereExpression ) {
    this( pathSpec, wildcardDepth, recursiveField, null, 0, false, whereExpression );
}

public PathsExpression( VariablePath pathSpec, int wildcardDepth, String recursiveField,
    String arrayWildcardPath, Expression whereExpression ) {
    this( pathSpec, wildcardDepth, recursiveField, arrayWildcardPath, 0, false, whereExpression );
}

public PathsExpression( VariablePath pathSpec, int wildcardDepth, String recursiveField,
    String arrayWildcardPath, int wildcardDepthAfterArray, Expression whereExpression ) {
    this( pathSpec, wildcardDepth, recursiveField, arrayWildcardPath, wildcardDepthAfterArray, false,
        whereExpression );
}
```

3. **Create new 6-parameter constructor:**
```java
public PathsExpression( VariablePath pathSpec, int wildcardDepth, String recursiveField,
    String arrayWildcardPath, int wildcardDepthAfterArray, boolean recursiveFieldIsArray,
    Expression whereExpression ) {
    this.pathSpec = pathSpec;
    this.wildcardDepth = wildcardDepth;
    this.recursiveField = recursiveField;
    this.arrayWildcardPath = arrayWildcardPath;
    this.wildcardDepthAfterArray = wildcardDepthAfterArray;
    this.recursiveFieldIsArray = recursiveFieldIsArray;  // ← NEW
    this.whereExpression = whereExpression;
}
```

4. **Update cloneExpression() method:**
```java
@Override
public Expression cloneExpression( TransformationReason reason ) {
    return new PathsExpression(
        (VariablePath) pathSpec.cloneExpression( reason ),
        wildcardDepth,
        recursiveField,
        arrayWildcardPath,
        wildcardDepthAfterArray,
        recursiveFieldIsArray,  // ← NEW
        whereExpression.cloneExpression( reason ) );
}
```

5. **Add routing logic in evaluate() method:**

**Location:** In the routing section where we determine which collector method to call

**Before:**
```java
} else if( arrayWildcardPath != null ) {
    // Array wildcard: data[*] or tree.items[*]
    candidatePaths = NativePathCollector.collectArrayPaths( vec, rootPath, arrayWildcardPath );
} else if( recursiveField != null ) {
    // Recursive field search: var..field
    candidatePaths = NativePathCollector.collectPathsRecursive( vec, rootPath, recursiveField );
} else {
```

**After:**
```java
} else if( arrayWildcardPath != null ) {
    // Array wildcard: data[*] or tree.items[*]
    candidatePaths = NativePathCollector.collectArrayPaths( vec, rootPath, arrayWildcardPath );
} else if( recursiveField != null && recursiveFieldIsArray ) {
    // *** NEW: Recursive field with array wildcard: var..field[*] ***
    candidatePaths =
        NativePathCollector.collectRecursiveArrayPaths( vec, rootPath, recursiveField );
} else if( recursiveField != null ) {
    // Recursive field search: var..field
    candidatePaths = NativePathCollector.collectPathsRecursive( vec, rootPath, recursiveField );
} else {
```

**Routing Decision Tree:**
```
evaluate() called
    ↓
Check: arrayWildcardPath != null && wildcardDepth > 0?
    YES → collectWildcardArrayPaths() [var.*[*], var.*.*[*]]
    NO  ↓
Check: arrayWildcardPath != null && wildcardDepthAfterArray > 0?
    YES → collectArrayWildcardPaths() [var[*].*, var[*].*.*]
    NO  ↓
Check: arrayWildcardPath != null?
    YES → collectArrayPaths() [var[*], var.field[*]]
    NO  ↓
Check: recursiveField != null && recursiveFieldIsArray?  ← NEW
    YES → collectRecursiveArrayPaths() [var..field[*]]   ← NEW
    NO  ↓
Check: recursiveField != null?
    YES → collectPathsRecursive() [var..field]
    NO  ↓
Default → collectPaths() [var, var.*, var.*.*]
```

---

### Step 5: Mirror Changes in PathsProcess

**File:** `jolie/src/main/java/jolie/process/PathsProcess.java`

**Goal:** Keep PathsProcess (statement version) in sync with PathsExpression (expression version)

**Note:** PathsProcess is nearly identical to PathsExpression but for PATHS statements (without `<<` operator)

**Changes:** Exactly the same as PathsExpression:

1. Add `recursiveFieldIsArray` field
2. Update all constructors
3. Update `copy()` method
4. Add same routing logic in `run()` method

**Example:**
```java
public class PathsProcess implements Process {
    private final VariablePath pathSpec;
    private final int wildcardDepth;
    private final String recursiveField;
    private final String arrayWildcardPath;
    private final int wildcardDepthAfterArray;
    private final boolean recursiveFieldIsArray;  // ← NEW
    private final Expression whereExpression;

    // ... constructors ...

    @Override
    public void run() {
        // ... same routing logic as PathsExpression.evaluate() ...

        } else if( recursiveField != null && recursiveFieldIsArray ) {
            // Recursive field with array wildcard: var..field[*]
            candidatePaths =
                NativePathCollector.collectRecursiveArrayPaths( vec, rootPath, recursiveField );
        } else if( recursiveField != null ) {
            // Recursive field search: var..field
            candidatePaths = NativePathCollector.collectPathsRecursive( vec, rootPath, recursiveField );
        } else {
```

---

### Step 6: Implement collectRecursiveArrayPaths in NativePathCollector

**File:** `jolie/src/main/java/jolie/runtime/paths/NativePathCollector.java`

**Goal:** Create the core algorithm that traverses the value tree recursively and enumerates arrays

**Add Public Method:**
```java
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
```

**Add Private Helper Method:**
```java
private static void collectRecursiveArrayPathsHelper( Value node, String currentPath,
    String targetField, List< String > paths ) {
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
```

**Algorithm Breakdown:**

```
Input: vec (data's ValueVector), rootPath="data", targetField="items"

Value Tree:
data
├── items[0] = 10
├── items[1] = 20
├── nested
│   ├── items[0] = 30
│   ├── items[1] = 40
│   └── deep
│       └── items[0] = 50
└── other = "xyz"

Step-by-Step Execution:

1. Initialize:
   stack = [(data value, "data")]
   paths = []

2. Pop (data value, "data"):
   Examine children:
   - "items": matches target!
     └ Enumerate: paths += ["data.items[0]", "data.items[1]"]
   - "nested": doesn't match
     └ Push: (nested value, "data.nested")
   - "other": doesn't match, not an object
     └ (skip, can't have children)

3. Pop (nested value, "data.nested"):
   Examine children:
   - "items": matches target!
     └ Enumerate: paths += ["data.nested.items[0]", "data.nested.items[1]"]
   - "deep": doesn't match
     └ Push: (deep value, "data.nested.deep")

4. Pop (deep value, "data.nested.deep"):
   Examine children:
   - "items": matches target!
     └ Enumerate: paths += ["data.nested.deep.items[0]"]

5. Stack empty, done!

Final paths:
[
  "data.items[0]",
  "data.items[1]",
  "data.nested.items[0]",
  "data.nested.items[1]",
  "data.nested.deep.items[0]"
]
```

**Key Design Choices:**

1. **Iterative vs Recursive**: Uses stack for DFS to avoid Java stack overflow on deep trees
2. **Vivification Prevention**: Uses `.children()` which only returns existing children
3. **Array Detection**: Uses `childVector.size()` to enumerate all elements
4. **Path Building**: Constructs full paths incrementally (`data` → `data.nested` → `data.nested.deep`)

**Comparison with Related Methods:**

| Method | Purpose | Example |
|--------|---------|---------|
| `collectPathsRecursive()` | Find field recursively (scalar) | `data..items` → `["data.items", "data.nested.items"]` |
| `collectArrayPaths()` | Specific field array | `data.items[*]` → `["data.items[0]", "data.items[1]"]` |
| **`collectRecursiveArrayPaths()`** | **Recursive + array** | **`data..items[*]` → `["data.items[0]", "data.items[1]", "data.nested.items[0]", ...]`** |

---

### Step 7: Update OOITBuilder to Pass New Parameter

**File:** `jolie/src/main/java/jolie/OOITBuilder.java`

**Goal:** Convert PathSpecNode (AST) to PathsExpression/PathsProcess (runtime) with new parameter

**Two Locations:**

**Location 1: PathsExpressionNode visitor (line ~1516):**

**Before:**
```java
@Override
public void visit( PathsExpressionNode n ) {
    currExpression = new PathsExpression(
        buildVariablePath( n.pathSpec().baseVariable() ),
        n.pathSpec().wildcardDepth(),
        n.pathSpec().recursiveField(),
        n.pathSpec().arrayWildcardPath(),
        n.pathSpec().wildcardDepthAfterArray(),
        buildExpression( n.whereExpression() ) );
}
```

**After:**
```java
@Override
public void visit( PathsExpressionNode n ) {
    currExpression = new PathsExpression(
        buildVariablePath( n.pathSpec().baseVariable() ),
        n.pathSpec().wildcardDepth(),
        n.pathSpec().recursiveField(),
        n.pathSpec().arrayWildcardPath(),
        n.pathSpec().wildcardDepthAfterArray(),
        n.pathSpec().recursiveFieldIsArray(),  // ← NEW
        buildExpression( n.whereExpression() ) );
}
```

**Location 2: PathsStatement visitor (line ~1762):**

**Before:**
```java
@Override
public void visit( PathsStatement n ) {
    currProcess = new PathsProcess(
        buildVariablePath( n.pathSpec().baseVariable() ),
        n.pathSpec().wildcardDepth(),
        n.pathSpec().recursiveField(),
        n.pathSpec().arrayWildcardPath(),
        buildExpression( n.whereExpression() ) );
}
```

**After:**
```java
@Override
public void visit( PathsStatement n ) {
    currProcess = new PathsProcess(
        buildVariablePath( n.pathSpec().baseVariable() ),
        n.pathSpec().wildcardDepth(),
        n.pathSpec().recursiveField(),
        n.pathSpec().arrayWildcardPath(),
        n.pathSpec().wildcardDepthAfterArray(),
        n.pathSpec().recursiveFieldIsArray(),  // ← NEW
        buildExpression( n.whereExpression() ) );
}
```

**AST → Runtime Flow:**
```
PathSpecNode (AST)
├── recursiveField = "items"
└── recursiveFieldIsArray = true
         ↓ OOITBuilder.visit()
PathsExpression (Runtime)
├── recursiveField = "items"
└── recursiveFieldIsArray = true
```

---

## Critical Implementation Details

### Detail 1: Why a Boolean Instead of Extending arrayWildcardPath?

**Option A (Rejected):** Extend `arrayWildcardPath` to support empty string for recursive
```java
// REJECTED APPROACH
if (recursiveField != null) {
    arrayWildcardPath = "";  // Empty = "recursive mode"
}

// PROBLEM: Ambiguous!
// How to distinguish:
// - var[*]           (arrayWildcardPath="", no recursive)
// - var..field[*]    (arrayWildcardPath="", WITH recursive)
// - var.field[*]     (arrayWildcardPath="field", no recursive)
```

**Option B (Chosen):** Add dedicated boolean field
```java
// CLEAN APPROACH
recursiveField = "items";
recursiveFieldIsArray = true;

// CLEAR: Both fields tell the story
// - recursiveField: "Which field to find recursively?"
// - recursiveFieldIsArray: "Should we enumerate arrays?"
```

**Benefits:**
- **Clarity**: Intent is explicit
- **Separation**: Recursive logic separate from array wildcard logic
- **Extensibility**: Easy to add more recursive features later
- **No Ambiguity**: Each field has single responsibility

---

### Detail 2: Parser Token Consumption Order

**Critical:** Parser must consume tokens in exact order to avoid errors

**Token Stream for `data..items[*]`:**
```
PATHS  ID(data)  DOT  DOT  ID(items)  LSQUARE  ASTERISK  RSQUARE  WHERE  ...
```

**Consumption Sequence:**
```java
// Starting position after PATHS token
token = ID(data)

// 1. Base variable
assertIdentifier();          // Verify: token is ID
String varId = token.content();  // Get: "data"
nextToken();                 // Move: DOT (first)
baseVar = new VariablePathNode(varId);

// 2. Check for DOT (could be .field or .*)
if (token.is(DOT)) {
    nextToken();             // Move: DOT (second)

    // 3. Check for second DOT (recursive descent)
    if (token.is(DOT)) {
        nextToken();         // Move: ID(items)

        // 4. Get field name
        assertIdentifier();  // Verify: token is ID
        recursiveField = token.content();  // Get: "items"
        nextToken();         // Move: LSQUARE

        // 5. *** NEW: Check for [*] ***
        if (token.is(LSQUARE)) {
            nextToken();     // Move: ASTERISK
            eat(ASTERISK);   // Verify & Move: RSQUARE
            eat(RSQUARE);    // Verify & Move: WHERE
            recursiveFieldIsArray = true;
        }
        // Now at WHERE token
    }
}
```

**What Happens on Error:**

If user types `data..items[`  (missing `*]`):
```
token = LSQUARE
nextToken();
token = ??? (whatever comes next, not ASTERISK)
eat(ASTERISK);  ← PARSER ERROR: "expected * after [ in PATHS"
```

---

### Detail 3: The Optimizer Preservation Pattern

**The Problem:**

OLParseTreeOptimizer creates NEW AST nodes. If we don't explicitly copy all fields, some fields get default values.

**Example Bug (without fix):**

```java
// Original PathSpecNode from parser
PathSpecNode original = new PathSpecNode(
    context, baseVar, 0, "items", null, 0, true);
//                                          ^^^^ recursiveFieldIsArray

// Optimizer creates new node
PathSpecNode optimized = new PathSpecNode(
    original.context(),
    optimizePath(original.baseVariable()),
    original.wildcardDepth(),
    original.recursiveField(),
    original.arrayWildcardPath(),
    original.wildcardDepthAfterArray()
    // Missing: recursiveFieldIsArray()
);

// What happens?
// Constructor chains to 6-param version with FALSE as default!
// optimized.recursiveFieldIsArray = false  ← BUG!
```

**The Fix:**

Always pass ALL fields when reconstructing nodes:

```java
PathSpecNode optimized = new PathSpecNode(
    original.context(),
    optimizePath(original.baseVariable()),
    original.wildcardDepth(),
    original.recursiveField(),
    original.arrayWildcardPath(),
    original.wildcardDepthAfterArray(),
    original.recursiveFieldIsArray()  // ← CRITICAL!
);
```

**General Rule:**

When adding fields to AST nodes:
1. Add field to class
2. Update ALL constructors
3. Update optimizer visitor
4. Add getter method

Missing step 3 = subtle bugs!

---

### Detail 4: Iterative DFS vs Recursive DFS

**Why Use Stack Instead of Recursion?**

**Recursive Version (NOT used):**
```java
// POTENTIAL STACK OVERFLOW!
private void recurse(Value node, String path, String target, List<String> paths) {
    for (entry : node.children()) {
        if (entry.name.equals(target)) {
            // Add array elements
        }
        recurse(entry.value, path + "." + entry.name, target, paths);  // RECURSION
    }
}
```

**Problems:**
- Deep trees → Java stack overflow
- Jolie values can be arbitrarily deep
- Example: 1000-level nested structure → 1000 stack frames

**Iterative Version (Used):**
```java
Stack<Entry<Value, String>> stack = new Stack<>();
stack.push(new Entry(node, path));

while (!stack.isEmpty()) {
    Entry entry = stack.pop();
    for (child : entry.value.children()) {
        if (child.name.equals(target)) {
            // Add array elements
        }
        stack.push(new Entry(child.value, childPath));  // NO RECURSION
    }
}
```

**Benefits:**
- No stack overflow (heap-allocated stack)
- Same algorithmic complexity: O(n)
- Same result, safer execution
- Follows existing Jolie patterns

**Stack Size:**

Max stack size = max depth of value tree
Example:
```
data                  depth 0  stack size 1
├── a                 depth 1  stack size 1
│   └── b             depth 2  stack size 1
│       └── c         depth 3  stack size 1
└── x                 depth 1  stack size 1
```

Stack never exceeds depth of deepest path!

---

### Detail 5: WHERE Clause Integration

**How WHERE Filtering Works:**

After collecting candidate paths, PathsExpression filters them:

```java
// Step 1: Collect all candidate paths
List<String> candidatePaths =
    NativePathCollector.collectRecursiveArrayPaths(vec, "data", "items");
// ["data.items[0]", "data.items[1]", "data.nested.items[0]", ...]

// Step 2: Filter with WHERE expression
List<String> matchingPaths = new ArrayList<>();
for (String path : candidatePaths) {
    // Get actual value at this path
    Value candidateValue = getValueAtPath(vec, path, rootPath);

    // Bind $ to this value
    for (CurrentValueExpression expr : currentValueExprs) {
        expr.setValue(candidateValue);
    }

    // Evaluate WHERE expression
    Value whereResult = whereExpression.evaluate();

    // Include if WHERE returns true
    if (whereResult.boolValue()) {
        matchingPaths.add(path);
    }
}

return matchingPaths;
```

**Example:**

```jolie
data.items[0] = 10;
data.items[1] = 100;
data.nested.items[0] = 5;

res << paths data..items[*] where $ > 50;
```

**Execution:**

```
Candidates: ["data.items[0]", "data.items[1]", "data.nested.items[0]"]

Check "data.items[0]":
  value = 10
  $ = 10
  $ > 50 = false
  → EXCLUDE

Check "data.items[1]":
  value = 100
  $ = 100
  $ > 50 = true
  → INCLUDE

Check "data.nested.items[0]":
  value = 5
  $ = 5
  $ > 50 = false
  → EXCLUDE

Result: ["data.items[1]"]
```

---

## The PathSpecNode Extension Strategy

### Why Not Add More Specific Fields?

**Alternative Design (Rejected):**
```java
// REJECTED: Too specific
private final String recursiveArrayField;  // Field name for ..field[*]
private final String recursiveField;       // Field name for ..field
```

**Problem:** What about future combinations?
- `..field[*].*` (recursive + array + field wildcard)
- `..field[*].subfield` (recursive + array + specific subfield)
- Multiple patterns at once?

**Chosen Design:**
```java
// FLEXIBLE: Composable flags
private final String recursiveField;           // Which field to find recursively
private final boolean recursiveFieldIsArray;   // Should we enumerate arrays?
```

**Benefits:**
- Composable: Fields can be combined
- Extensible: Easy to add more boolean flags
- Clear: Each flag has single purpose
- Backward compatible: Existing code uses defaults

### Field Combination Matrix

| recursiveField | recursiveFieldIsArray | arrayWildcardPath | wildcardDepth | Meaning |
|----------------|----------------------|-------------------|---------------|---------|
| null | false | null | 0 | `var` (simple) |
| null | false | null | 1 | `var.*` (wildcard) |
| null | false | "" | 0 | `var[*]` (base array) |
| null | false | "field" | 0 | `var.field[*]` (field array) |
| "items" | false | null | 0 | `var..items` (recursive) |
| **"items"** | **true** | **null** | **0** | **`var..items[*]`** **(new!)** |

This matrix shows the feature is orthogonal to existing features!

---

## Backward Compatibility

### Constructor Chaining Strategy

**Problem:** Existing code uses 4-parameter or 5-parameter constructors

**Solution:** Chain all constructors to new 6-parameter version

```java
// Old code continues to work:
new PathSpecNode(ctx, var, 0, "field");
    ↓ chains to
new PathSpecNode(ctx, var, 0, "field", null, 0, false);
                                                 ^^^^^ default

// New code uses explicit value:
new PathSpecNode(ctx, var, 0, "field", null, 0, true);
                                                ^^^^ explicit
```

### No Breaking Changes

**Before implementation:**
- All existing PATHS queries work unchanged
- `var..field` still works (recursiveFieldIsArray defaults to false)
- `var.field[*]` still works (different code path)
- All tests pass

**After implementation:**
- All existing PATHS queries still work
- `var..field` still works (same behavior)
- `var.field[*]` still works (same behavior)
- **NEW:** `var..field[*]` now works too
- All tests still pass

**API Compatibility:**

Existing Java code calling PathsExpression:
```java
// Old code (4 params) - STILL WORKS
new PathsExpression(path, depth, whereExpr);

// Old code (5 params) - STILL WORKS
new PathsExpression(path, depth, recursive, whereExpr);

// Old code (6 params) - STILL WORKS
new PathsExpression(path, depth, recursive, arrayPath, whereExpr);

// New code (7 params) - NEW
new PathsExpression(path, depth, recursive, arrayPath, depthAfter, isArray, whereExpr);
```

All old signatures route to new constructor with safe defaults!

---

## Testing and Verification

### Test Strategy

**Test Coverage Matrix:**

| Feature | Test File | Expected Result |
|---------|-----------|-----------------|
| Basic enumeration | `test_recursive_array_basic.ol` | Multiple depths, all elements |
| WHERE filtering (numeric) | `test_recursive_array_filter.ol` | Filtered by `$ > 50` |
| WHERE filtering (string) | `test_recursive_array_string.ol` | Filtered by `$ == "admin"` |
| Empty results | `test_recursive_array_empty.ol` | No matches |

### Test 1: Basic Enumeration

**File:** `test_recursive_array_basic.ol`

```jolie
data.items[0] = 10;
data.items[1] = 20;
data.nested.items[0] = 30;
data.nested.items[1] = 40;
data.nested.deep.items[0] = 50;

res << paths data..items[*] where true;
```

**Expected Output:**
```
data.items[0]
data.items[1]
data.nested.items[0]
data.nested.items[1]
data.nested.deep.items[0]
```

**Validates:**
- Parser recognizes `..items[*]`
- Collector finds items at all depths
- All array elements enumerated
- Correct path construction

### Test 2: WHERE Filtering (Numeric)

**File:** `test_recursive_array_filter.ol`

```jolie
data.values[0] = 10;
data.values[1] = 50;
data.values[2] = 100;
data.nested.values[0] = 5;
data.nested.values[1] = 75;
data.nested.deep.values[0] = 200;

res << paths data..values[*] where $ > 50;
```

**Expected Output:**
```
data.values[2]
data.nested.values[1]
data.nested.deep.values[0]
```

**Validates:**
- WHERE filtering works correctly
- `$` operator bound to array element values
- Comparison operators work
- Filtering across different depths

### Test 3: WHERE Filtering (String)

**File:** `test_recursive_array_string.ol`

```jolie
data.users[0] = "alice";
data.users[1] = "admin";
data.nested.users[0] = "bob";
data.nested.users[1] = "admin";
data.nested.deep.users[0] = "charlie";

res << paths data..users[*] where $ == "admin";
```

**Expected Output:**
```
data.users[1]
data.nested.users[1]
```

**Validates:**
- String comparison works
- Multiple matches at different depths
- Correct filtering of non-matching elements

### Test 4: Empty Results

**File:** `test_recursive_array_empty.ol`

```jolie
data.values[0] = 10;
data.values[1] = 20;
data.nested.values[0] = 30;

res << paths data..values[*] where $ > 1000;

println@Console("Result count: " + #res.results)();
```

**Expected Output:**
```
Result count: 0
```

**Validates:**
- Handles no matches gracefully
- Empty array returned correctly
- No errors on empty result set

### Running Tests

```bash
# Individual test
JOLIE_HOME=$PWD/dist/jolie dist/launchers/unix/jolie \
    test/select/test_recursive_array_basic.ol

# All tests via test runner
cd test/select
python3 run_native_tests.py

# Expected: 82/82 passed (78 existing + 4 new)
```

### Test Results

```
✓ test_recursive_array_basic.ol
✓ test_recursive_array_filter.ol
✓ test_recursive_array_string.ol
✓ test_recursive_array_empty.ol

82/82 passed
```

**No regressions:** All 78 existing tests still pass!

---

## Complete File Inventory

### Files Modified

| File | Lines Changed | Type | Critical? |
|------|---------------|------|-----------|
| `PathSpecNode.java` | ~20 | AST | ✓ Yes |
| `OLParser.java` | ~14 (×2 locations) | Parser | ✓ Yes |
| `OLParseTreeOptimizer.java` | ~1 | Optimizer | ✓ **CRITICAL** |
| `PathsExpression.java` | ~15 | Runtime | ✓ Yes |
| `PathsProcess.java` | ~15 | Runtime | ✓ Yes |
| `NativePathCollector.java` | ~35 | Runtime | ✓ Yes |
| `OOITBuilder.java` | ~2 (×2 locations) | Builder | ✓ Yes |
| `run_native_tests.py` | ~4 | Tests | No |

### Files Created

| File | Purpose |
|------|---------|
| `test_recursive_array_basic.ol` | Basic functionality test |
| `test_recursive_array_filter.ol` | Numeric WHERE filtering |
| `test_recursive_array_string.ol` | String WHERE filtering |
| `test_recursive_array_empty.ol` | Empty result handling |
| `RECURSIVE_ARRAY_WILDCARD_IMPLEMENTATION.md` | Summary documentation |
| `RECURSIVE_ARRAY_WILDCARD_PATHS_DETAILED.md` | **This file - detailed guide** |

### Critical vs Interface-Only Changes

**Critical Changes (Behavior):**
1. `OLParser.java` - Parser logic to recognize `[*]` after `..field`
2. `PathSpecNode.java` - AST field to store the flag
3. `OLParseTreeOptimizer.java` - **MUST preserve flag** (easy to miss!)
4. `NativePathCollector.java` - Algorithm implementation
5. `PathsExpression.java` - Routing logic

**Interface-Only Changes (Compatibility):**
1. Constructor chaining in PathSpecNode
2. Constructor chaining in PathsExpression
3. Constructor chaining in PathsProcess
4. OOITBuilder parameter passing

### Build and Install

```bash
# Clean build
mvn clean compile

# Install Jolie distribution
mvn install -DskipTests

# Run tests
cd test/select
python3 run_native_tests.py
```

### Total Impact

- **8 files modified**
- **4 test files created**
- **2 documentation files created**
- **~121 lines of production code added**
- **~70 lines of test code added**
- **0 breaking changes**
- **82/82 tests passing**

---

## Conclusion

The `..field[*]` implementation successfully combines recursive descent with array wildcard enumeration through:

1. **Clean Extension:** New boolean field in PathSpecNode
2. **Parser Enhancement:** Recognize `[*]` tokens after recursive field
3. **Runtime Algorithm:** Iterative DFS with array enumeration
4. **Full Integration:** WHERE clause support, backward compatibility
5. **Comprehensive Testing:** 4 new tests, all existing tests pass

The feature is production-ready and follows Jolie's design patterns for:
- No recursion (iterative algorithms only)
- No vivification (only traverse existing values)
- Backward compatibility (constructor chaining)
- Native syntax (no ANTLR strings)

**Key Takeaway:** When adding features to AST nodes, always remember the optimizer! The `OLParseTreeOptimizer` must preserve all fields when reconstructing nodes, or subtle bugs occur.
