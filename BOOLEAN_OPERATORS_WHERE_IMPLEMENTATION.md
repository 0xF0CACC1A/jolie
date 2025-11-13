# Adding Boolean Operators Support to PATHS WHERE Clauses

## Table of Contents
1. [Overview](#overview)
2. [Architecture Before vs After](#architecture-before-vs-after)
3. [The Core Problem: CurrentValueExpression Binding](#the-core-problem-currentvalueexpression-binding)
4. [Implementation Steps](#implementation-steps)
5. [Expression Tree Traversal Strategy](#expression-tree-traversal-strategy)
6. [Testing Complex Boolean Expressions](#testing-complex-boolean-expressions)
7. [Complete File Inventory](#complete-file-inventory)
8. [Lessons Learned](#lessons-learned)

---

## Overview

### Goal
Enable full boolean operator support in PATHS WHERE clauses, allowing complex filtering expressions with `&&` (AND), `||` (OR), and `!` (NOT).

**Before (Limited Support):**
```jolie
// Simple comparisons worked
result << paths tree.* where $ == 5

// Boolean operators FAILED - "$ not bound" error
result << paths tree.* where $ > 5 && $ < 20  // ✗ BROKEN
```

**After (Full Boolean Support):**
```jolie
// Simple comparisons still work
result << paths tree.* where $ == 5

// Boolean operators now work
result << paths tree.* where $ > 5 && $ < 20               // ✓ AND
result << paths tree.* where $ < 5 || $ > 20               // ✓ OR
result << paths tree.* where !($ > 10)                     // ✓ NOT
result << paths tree.* where $ > 0 && ($ < 10 || $ > 20)   // ✓ Complex

// Works with recursive field descent too!
result << paths tree.* where $..score > 10 && $.priority > 5  // ✓ Mixed
```

### Why This Was Needed

The initial native WHERE implementation (converting from ANTLR strings to Jolie expressions) only handled **simple comparisons** where `$` appeared once:

```jolie
where $ == 5      // ✓ Single $ - worked
where $ > 10      // ✓ Single $ - worked
where $ > 5 && $ < 20   // ✗ Multiple $ - FAILED!
```

Boolean operators create **expression trees with multiple `$` references**, and the original implementation only bound the first `$` it found.

### Key Challenges

1. **Multiple `$` instances**: Boolean expressions can contain multiple `CurrentValueExpression` objects that all need binding
2. **Nested expression trees**: Boolean operators create deep tree structures (AND/OR/NOT nodes)
3. **Hidden fields**: The `children` and `expression` fields in boolean operator classes were private
4. **Traversal complexity**: Finding all `$` references requires recursive tree traversal

---

## Architecture Before vs After

### Before: Single CurrentValueExpression Binding

```
WHERE Expression: $ > 5 && $ < 20
                  ↓
Expression Tree:
    AndCondition
    ├─ CompareCondition ($ > 5)
    │   ├─ CurrentValueExpression (1)  ← $ reference
    │   └─ ValueImpl(5)
    └─ CompareCondition ($ < 20)
        ├─ CurrentValueExpression (2)  ← $ reference (UNBOUND!)
        └─ ValueImpl(20)

Filtering Loop:
  for candidate in candidates:
    currentValueExpr = findCurrentValueExpression(whereExpression)
    // ↑ Only finds CurrentValueExpression (1)

    currentValueExpr.setCurrentNode(candidate)
    // ↑ Only binds first $, second $ remains unbound!

    result = whereExpression.evaluate()
    // ↑ CRASH: "$ not bound" when evaluating second comparison
```

**The bug**: `findCurrentValueExpression()` returned only the **first** `CurrentValueExpression` it found, leaving others unbound.

**Symptom**:
```
java.lang.IllegalStateException: $ not bound
    at jolie.runtime.expression.CurrentValueExpression.evaluate(CurrentValueExpression.java:47)
    at jolie.runtime.expression.CompareCondition.evaluate(CompareCondition.java:55)
    at jolie.runtime.expression.AndCondition.evaluate(AndCondition.java:60)
```

### After: All CurrentValueExpression Instances Bound

```
WHERE Expression: $ > 5 && $ < 20
                  ↓
Expression Tree:
    AndCondition
    ├─ CompareCondition ($ > 5)
    │   ├─ CurrentValueExpression (1)  ← $ reference
    │   └─ ValueImpl(5)
    └─ CompareCondition ($ < 20)
        ├─ CurrentValueExpression (2)  ← $ reference
        └─ ValueImpl(20)

Filtering Loop:
  for candidate in candidates:
    List<CurrentValueExpression> allExprs = []
    findAllCurrentValueExpressions(whereExpression, allExprs)
    // ↑ Recursively finds BOTH CurrentValueExpression (1) and (2)

    for expr in allExprs:
      expr.setCurrentNode(candidate)
    // ↑ Binds ALL $ references to current candidate

    result = whereExpression.evaluate()
    // ↑ SUCCESS: All $ references are bound
```

**The fix**: `findAllCurrentValueExpressions()` **recursively traverses** the entire expression tree, collecting all `CurrentValueExpression` instances into a list, then binds all of them.

---

## The Core Problem: CurrentValueExpression Binding

### Understanding CurrentValueExpression

`CurrentValueExpression` is a special expression type that holds a **mutable reference** to "the current value being filtered":

```java
public class CurrentValueExpression implements Expression {
    private Value currentNode;  // ← Mutable state

    public void setCurrentNode(Value node) {
        this.currentNode = node;  // ← Must be called before evaluate()
    }

    @Override
    public Value evaluate() {
        if (currentNode == null)
            throw new IllegalStateException("$ not bound");
        return currentNode;
    }
}
```

**Critical insight**: Each `$` in the WHERE clause creates a **separate instance** of `CurrentValueExpression`. All instances must be bound to the same candidate value before evaluation.

### The Binding Problem Visualized

**Expression**: `paths tree.* where $ > 5 && $ < 20`

**Expression Tree Construction** (happens once at parse time):
```
AndCondition {
  children = [
    CompareCondition {
      left = new CurrentValueExpression()   // Instance A
      right = ValueImpl(5)
    },
    CompareCondition {
      left = new CurrentValueExpression()   // Instance B
      right = ValueImpl(20)
    }
  ]
}
```

**Evaluation Loop** (happens for each candidate):
```java
// Candidate 1: tree.a = 3
Instance A.setCurrentNode(3)  // ← Must bind
Instance B.setCurrentNode(3)  // ← Must bind
evaluate() → (3 > 5 && 3 < 20) → false

// Candidate 2: tree.b = 10
Instance A.setCurrentNode(10)  // ← Must bind
Instance B.setCurrentNode(10)  // ← Must bind
evaluate() → (10 > 5 && 10 < 20) → true
```

**The bug**: Original code only found and bound **Instance A**, leaving **Instance B** unbound.

### Why findCurrentValueExpression() Was Insufficient

**Original implementation** (BUGGY):
```java
private CurrentValueExpression findCurrentValueExpression(Expression expr) {
    if (expr instanceof CurrentValueExpression) {
        return (CurrentValueExpression) expr;  // ← Found one, return immediately
    }

    // For comparison expressions, check operands
    if (expr instanceof CompareCondition) {
        CompareCondition cmp = (CompareCondition) expr;

        // Check left operand
        if (cmp.leftExpression() instanceof CurrentValueExpression) {
            return (CurrentValueExpression) cmp.leftExpression();  // ← Return first match
        }

        // Check right operand
        if (cmp.rightExpression() instanceof CurrentValueExpression) {
            return (CurrentValueExpression) cmp.rightExpression();
        }
    }

    // For other composite expressions, would need more traversal
    return null;  // ← No boolean operator traversal!
}
```

**Problems**:
1. **Returns single instance**: `return` stops search after first match
2. **No boolean operator traversal**: Doesn't know how to traverse `AndCondition`, `OrCondition`, or `NotExpression`
3. **Shallow search**: Only looks one level deep (immediate children of `CompareCondition`)

**Example failure**:
```java
Expression: $ > 5 && $ < 20

Tree structure:
  AndCondition
  ├─ CompareCondition ($ > 5)  ← findCurrentValueExpression() finds $ here
  └─ CompareCondition ($ < 20)  ← Never reaches this $

Result: Only first $ is bound, second $ causes "not bound" error
```

---

## Implementation Steps

### Step 1: Make Boolean Operator Fields Accessible

**Problem**: The expression classes `AndCondition`, `OrCondition`, and `NotExpression` had **private fields** with no accessors:

```java
public class AndCondition implements Expression {
    private final Expression[] children;  // ← Can't access!
}

public class OrCondition implements Expression {
    final private Expression[] children;  // ← Can't access!
}

public class NotExpression implements Expression {
    private final Expression expression;  // ← Can't access!
}
```

**Solution**: Change fields to **public** for direct access:

#### File 1: AndCondition.java

**File**: `jolie/src/main/java/jolie/runtime/expression/AndCondition.java`

**Before (line 38):**
```java
public class AndCondition implements Expression {
	private final Expression[] children;
```

**After:**
```java
public class AndCondition implements Expression {
	public final Expression[] children;
```

**Why necessary**: `findAllCurrentValueExpressions()` needs to iterate over the child expressions to traverse the tree. Without access to `children`, we can't inspect the expression tree structure.

**Why public instead of getter**:
- Fields are `final`, so they can't be modified (safe to expose)
- Direct field access is simpler and more performant than getter methods
- Avoids adding boilerplate getter methods to expression classes

#### File 2: OrCondition.java

**File**: `jolie/src/main/java/jolie/runtime/expression/OrCondition.java`

**Before (line 30):**
```java
public class OrCondition implements Expression {
	final private Expression[] children;
```

**After:**
```java
public class OrCondition implements Expression {
	public final Expression[] children;
```

**Why necessary**: Same reasoning as `AndCondition` - need to traverse children for OR expressions.

#### File 3: NotExpression.java

**File**: `jolie/src/main/java/jolie/runtime/expression/NotExpression.java`

**Before (line 29):**
```java
public class NotExpression implements Expression {
	private final Expression expression;
```

**After:**
```java
public class NotExpression implements Expression {
	public final Expression expression;
```

**Why necessary**: Need to access the negated expression to continue traversal through NOT operators.

---

### Step 2: Replace findCurrentValueExpression() with findAllCurrentValueExpressions()

This is the **core fix** - replacing shallow single-instance search with deep recursive collection.

#### File 4: PathsExpression.java - Binding Logic

**File**: `jolie/src/main/java/jolie/runtime/expression/PathsExpression.java`

**Before (lines 54-71):**
```java
// Filter candidates using native Jolie WHERE expression
List<String> matchingPaths = new ArrayList<>();
CurrentValueExpression currentValueExpr = findCurrentValueExpression(whereExpression);
// ↑ Find single instance

for (String path : candidatePaths) {
    Value candidateValue = getValueAtPath(vec, path, rootPath);
    if (candidateValue == null)
        continue;

    if (currentValueExpr != null) {
        currentValueExpr.setCurrentNode(candidateValue);
        // ↑ Bind only one instance
    }

    Value whereResult = whereExpression.evaluate();
    if (whereResult.boolValue()) {
        matchingPaths.add(path);
    }
}
```

**After:**
```java
// Filter candidates using native Jolie WHERE expression
List<String> matchingPaths = new ArrayList<>();
List<CurrentValueExpression> currentValueExprs = new ArrayList<>();
findAllCurrentValueExpressions(whereExpression, currentValueExprs);
// ↑ Find ALL instances recursively

for (String path : candidatePaths) {
    Value candidateValue = getValueAtPath(vec, path, rootPath);
    if (candidateValue == null)
        continue;

    // Bind all CurrentValueExpression instances to the candidate value
    for (CurrentValueExpression expr : currentValueExprs) {
        expr.setCurrentNode(candidateValue);
        // ↑ Bind ALL instances
    }

    Value whereResult = whereExpression.evaluate();
    if (whereResult.boolValue()) {
        matchingPaths.add(path);
    }
}
```

**Changes**:
1. Replaced `CurrentValueExpression currentValueExpr` (single) with `List<CurrentValueExpression> currentValueExprs` (all)
2. Changed `findCurrentValueExpression()` to `findAllCurrentValueExpressions()` with output parameter
3. Added loop to bind **all** collected expressions before evaluation

**Why necessary**: This is where the actual binding happens. We must bind **every** `$` reference before evaluating the WHERE expression.

---

#### File 4 continued: PathsExpression.java - Traversal Method

**Before (lines 81-99):**
```java
private CurrentValueExpression findCurrentValueExpression(Expression expr) {
    if (expr instanceof CurrentValueExpression) {
        return (CurrentValueExpression) expr;
    }

    // For comparison expressions, check operands
    if (expr instanceof CompareCondition) {
        CompareCondition cmp = (CompareCondition) expr;
        // Check left operand
        if (cmp.leftExpression() instanceof CurrentValueExpression) {
            return (CurrentValueExpression) cmp.leftExpression();
        }
        // Check right operand
        if (cmp.rightExpression() instanceof CurrentValueExpression) {
            return (CurrentValueExpression) cmp.rightExpression();
        }
    }

    // For other composite expressions, would need more traversal
    return null;
}
```

**After:**
```java
private void findAllCurrentValueExpressions(Expression expr, List<CurrentValueExpression> result) {
    if (expr instanceof CurrentValueExpression) {
        result.add((CurrentValueExpression) expr);
        return;
    }

    // Traverse comparison expressions
    if (expr instanceof CompareCondition) {
        CompareCondition cmp = (CompareCondition) expr;
        findAllCurrentValueExpressions(cmp.leftExpression(), result);
        findAllCurrentValueExpressions(cmp.rightExpression(), result);
        return;
    }

    // Traverse boolean operators
    if (expr instanceof AndCondition) {
        AndCondition and = (AndCondition) expr;
        for (Expression child : and.children) {
            findAllCurrentValueExpressions(child, result);
        }
        return;
    }

    if (expr instanceof OrCondition) {
        OrCondition or = (OrCondition) expr;
        for (Expression child : or.children) {
            findAllCurrentValueExpressions(child, result);
        }
        return;
    }

    if (expr instanceof NotExpression) {
        NotExpression not = (NotExpression) expr;
        findAllCurrentValueExpressions(not.expression, result);
        return;
    }
}
```

**Key differences**:

1. **Return type**: `void` instead of `CurrentValueExpression` - uses output parameter pattern
2. **Collection instead of return**: Adds to `result` list instead of returning single value
3. **Recursive traversal**: Calls itself recursively to traverse entire tree
4. **Boolean operator support**: Added cases for `AndCondition`, `OrCondition`, and `NotExpression`
5. **Exhaustive search**: Doesn't stop at first match - continues until entire tree is traversed

**Algorithm explanation**:

```
findAllCurrentValueExpressions(expr, result):
  if expr is CurrentValueExpression:
    result.add(expr)  ← Found one, collect it
    return

  if expr is CompareCondition:
    findAllCurrentValueExpressions(left child, result)   ← Recurse left
    findAllCurrentValueExpressions(right child, result)  ← Recurse right
    return

  if expr is AndCondition:
    for each child in children array:
      findAllCurrentValueExpressions(child, result)  ← Recurse all children
    return

  if expr is OrCondition:
    for each child in children array:
      findAllCurrentValueExpressions(child, result)  ← Recurse all children
    return

  if expr is NotExpression:
    findAllCurrentValueExpressions(negated expr, result)  ← Recurse through NOT
    return
```

**Example traversal** for `$ > 5 && $ < 20`:

```
Call: findAllCurrentValueExpressions(AndCondition, [])
  → AndCondition detected, iterate children:

  Child 1: CompareCondition ($ > 5)
    Call: findAllCurrentValueExpressions(CompareCondition, [])
      → CompareCondition detected, recurse left:

      Call: findAllCurrentValueExpressions(CurrentValueExpression, [])
        → CurrentValueExpression detected!
        → result.add(CurrentValueExpression)  // result = [CVE₁]

      → CompareCondition detected, recurse right:

      Call: findAllCurrentValueExpressions(ValueImpl(5), [])
        → Not a recognized type, return

  Child 2: CompareCondition ($ < 20)
    Call: findAllCurrentValueExpressions(CompareCondition, [])
      → CompareCondition detected, recurse left:

      Call: findAllCurrentValueExpressions(CurrentValueExpression, [])
        → CurrentValueExpression detected!
        → result.add(CurrentValueExpression)  // result = [CVE₁, CVE₂]

      → CompareCondition detected, recurse right:

      Call: findAllCurrentValueExpressions(ValueImpl(20), [])
        → Not a recognized type, return

Final result: [CVE₁, CVE₂]  ← Both $ references collected!
```

---

#### File 5: PathsProcess.java - Identical Changes

**File**: `jolie/src/main/java/jolie/process/PathsProcess.java`

**Changes**: Identical to `PathsExpression.java` - both need the fix because:
- `PathsExpression` is used with `<<` operator: `result << paths tree.* where $ > 5`
- `PathsProcess` is used as statement: `paths tree.* where $ > 5` (though less common)

**Modified lines 59-80** (binding loop):
```java
// Filter candidates using native Jolie WHERE expression
List<String> matchingPaths = new ArrayList<>();
List<CurrentValueExpression> currentValueExprs = new ArrayList<>();
findAllCurrentValueExpressions(whereExpression, currentValueExprs);

for (String path : candidatePaths) {
    Value candidateValue = getValueAtPath(vec, path, rootPath);
    if (candidateValue == null)
        continue;

    // Bind all CurrentValueExpression instances to the candidate value
    for (CurrentValueExpression expr : currentValueExprs) {
        expr.setCurrentNode(candidateValue);
    }

    Value whereResult = whereExpression.evaluate();
    if (whereResult.boolValue()) {
        matchingPaths.add(path);
    }
}
```

**Modified lines 85-121** (traversal method):
```java
private void findAllCurrentValueExpressions(Expression expr, List<CurrentValueExpression> result) {
    if (expr instanceof CurrentValueExpression) {
        result.add((CurrentValueExpression) expr);
        return;
    }

    // Traverse comparison expressions
    if (expr instanceof CompareCondition) {
        CompareCondition cmp = (CompareCondition) expr;
        findAllCurrentValueExpressions(cmp.leftExpression(), result);
        findAllCurrentValueExpressions(cmp.rightExpression(), result);
        return;
    }

    // Traverse boolean operators
    if (expr instanceof AndCondition) {
        AndCondition and = (AndCondition) expr;
        for (Expression child : and.children) {
            findAllCurrentValueExpressions(child, result);
        }
        return;
    }

    if (expr instanceof OrCondition) {
        OrCondition or = (OrCondition) expr;
        for (Expression child : or.children) {
            findAllCurrentValueExpressions(child, result);
        }
        return;
    }

    if (expr instanceof NotExpression) {
        NotExpression not = (NotExpression) expr;
        findAllCurrentValueExpressions(not.expression, result);
        return;
    }
}
```

**Why duplicate code**: Jolie separates processes (statements) from expressions. This is a fundamental architectural pattern, not code duplication that should be refactored.

---

## Expression Tree Traversal Strategy

### Tree Structure for Complex Expressions

Let's visualize how complex boolean expressions create nested tree structures:

#### Example 1: Simple AND - `$ > 5 && $ < 20`

```
AndCondition
├─ CompareCondition (GREATER_THAN)
│   ├─ CurrentValueExpression ($)  ← CVE #1
│   └─ ValueImpl(5)
└─ CompareCondition (LESS_THAN)
    ├─ CurrentValueExpression ($)  ← CVE #2
    └─ ValueImpl(20)

Traversal order:
1. Visit AndCondition
2. Visit left child (CompareCondition)
3. Visit left.left child (CurrentValueExpression) → Collect CVE #1
4. Visit left.right child (ValueImpl)
5. Visit right child (CompareCondition)
6. Visit right.left child (CurrentValueExpression) → Collect CVE #2
7. Visit right.right child (ValueImpl)

Result: [CVE #1, CVE #2]
```

#### Example 2: OR - `$ < 5 || $ > 20`

```
OrCondition
├─ CompareCondition (LESS_THAN)
│   ├─ CurrentValueExpression ($)  ← CVE #1
│   └─ ValueImpl(5)
└─ CompareCondition (GREATER_THAN)
    ├─ CurrentValueExpression ($)  ← CVE #2
    └─ ValueImpl(20)

Traversal: Same pattern as AND
Result: [CVE #1, CVE #2]
```

#### Example 3: NOT - `!($..score > 10)`

```
NotExpression
└─ CompareCondition (GREATER_THAN)
    ├─ CurrentValueExpression ($..score)  ← CVE with recursive field
    └─ ValueImpl(10)

Traversal order:
1. Visit NotExpression
2. Visit negated expression (CompareCondition)
3. Visit left child (CurrentValueExpression) → Collect CVE
4. Visit right child (ValueImpl)

Result: [CVE with recursive field descent]
```

#### Example 4: Complex Nested - `$ > 0 && ($ < 10 || $ > 20)`

```
AndCondition
├─ CompareCondition ($ > 0)
│   ├─ CurrentValueExpression ($)  ← CVE #1
│   └─ ValueImpl(0)
└─ OrCondition
    ├─ CompareCondition ($ < 10)
    │   ├─ CurrentValueExpression ($)  ← CVE #2
    │   └─ ValueImpl(10)
    └─ CompareCondition ($ > 20)
        ├─ CurrentValueExpression ($)  ← CVE #3
        └─ ValueImpl(20)

Traversal order:
1. Visit AndCondition
2. Visit left child (CompareCondition $ > 0)
3.   Visit CurrentValueExpression → Collect CVE #1
4. Visit right child (OrCondition)
5.   Visit left child (CompareCondition $ < 10)
6.     Visit CurrentValueExpression → Collect CVE #2
7.   Visit right child (CompareCondition $ > 20)
8.     Visit CurrentValueExpression → Collect CVE #3

Result: [CVE #1, CVE #2, CVE #3]
```

#### Example 5: Mixed with Recursive Fields - `$..status == "active" && $.priority > 5`

```
AndCondition
├─ CompareCondition (EQUAL)
│   ├─ CurrentValueExpression ($..status)  ← CVE #1 with recursive descent
│   └─ ValueImpl("active")
└─ CompareCondition (GREATER_THAN)
    ├─ CurrentValueExpression ($.priority)  ← CVE #2 with field path
    └─ ValueImpl(5)

Both CurrentValueExpression instances get bound:
- CVE #1 has recursiveField = "status"
- CVE #2 has fieldPath = ["priority"]

When evaluated:
- CVE #1.evaluate() → searches recursively for "status" field
- CVE #2.evaluate() → navigates to direct child "priority"
```

### Depth-First Traversal Algorithm

The `findAllCurrentValueExpressions()` method performs a **depth-first traversal** of the expression tree:

```
Algorithm: DFS-Find-All-CVE(expr, result)
Input: expr - current expression node
       result - accumulator list (passed by reference)

1. BASE CASE - Leaf node (CurrentValueExpression):
   if expr is CurrentValueExpression:
     result.append(expr)
     return

2. RECURSIVE CASE - Binary operators (CompareCondition):
   if expr is CompareCondition:
     DFS-Find-All-CVE(expr.leftExpression, result)   ← Go left
     DFS-Find-All-CVE(expr.rightExpression, result)  ← Go right
     return

3. RECURSIVE CASE - N-ary operators (AndCondition, OrCondition):
   if expr is AndCondition or OrCondition:
     for each child in expr.children:
       DFS-Find-All-CVE(child, result)  ← Visit all children
     return

4. RECURSIVE CASE - Unary operators (NotExpression):
   if expr is NotExpression:
     DFS-Find-All-CVE(expr.expression, result)  ← Follow through negation
     return

5. DEFAULT CASE - Unknown/leaf types (ValueImpl, VariableExpression, etc.):
   return  ← Not a CVE, not a container, stop traversal
```

**Time complexity**: O(n) where n is the number of nodes in the expression tree (visits each node exactly once)

**Space complexity**: O(d) where d is the depth of the tree (recursion stack) + O(c) where c is the number of CurrentValueExpression instances (result list)

---

## Testing Complex Boolean Expressions

### Test Suite Design Philosophy

We created **5 new tests** to verify boolean operator support:

1. **test_recursive_and.ol** - AND operator with range check
2. **test_recursive_or.ol** - OR operator with multiple conditions
3. **test_recursive_not.ol** - NOT operator with recursive field
4. **test_recursive_where_and_field.ol** - Mix recursive and direct field access
5. **test_recursive_complex.ol** - Nested parentheses with mixed operators

### Test 1: AND Operator - Range Filtering

**File**: `test/paths/test_recursive_and.ol`

```jolie
include "console.iol"

main {
    tree.a.value = 5;
    tree.b.data.value = 15;
    tree.c.nested.value = 25;
    tree.d.value = 8;

    res << paths tree..value where $ > 5 && $ < 20;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
```

**Test logic**:
- Find all `value` fields at any depth (recursive descent: `tree..value`)
- Filter for values in range (5, 20) - exclusive bounds
- Uses AND to check both conditions

**Expected output**:
```
tree.d.value
tree.b.data.value
```

**Why these results**:
- `tree.a.value = 5` → 5 > 5 is FALSE → filtered out
- `tree.b.data.value = 15` → 15 > 5 AND 15 < 20 → TRUE ✓
- `tree.c.nested.value = 25` → 25 < 20 is FALSE → filtered out
- `tree.d.value = 8` → 8 > 5 AND 8 < 20 → TRUE ✓

**What this tests**:
- Multiple `$` references in single WHERE clause
- AND operator functionality
- Both CurrentValueExpression instances get bound correctly

---

### Test 2: OR Operator - Multiple Alternatives

**File**: `test/paths/test_recursive_or.ol`

```jolie
include "console.iol"

main {
    tree.a.value = 3;
    tree.b.data.value = 12;
    tree.c.nested.value = 25;
    tree.d.value = 8;

    res << paths tree..value where $ < 5 || $ > 20;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
```

**Test logic**:
- Find all `value` fields at any depth
- Filter for values EITHER less than 5 OR greater than 20
- Uses OR for alternative conditions

**Expected output**:
```
tree.c.nested.value
tree.a.value
```

**Why these results**:
- `tree.a.value = 3` → 3 < 5 is TRUE → included ✓
- `tree.b.data.value = 12` → 12 < 5 is FALSE, 12 > 20 is FALSE → filtered out
- `tree.c.nested.value = 25` → 25 > 20 is TRUE → included ✓
- `tree.d.value = 8` → 8 < 5 is FALSE, 8 > 20 is FALSE → filtered out

**What this tests**:
- OR operator short-circuit evaluation
- Either condition can trigger match
- Order: Results appear in reverse tree traversal order

---

### Test 3: NOT Operator - Negation

**File**: `test/paths/test_recursive_not.ol`

```jolie
include "console.iol"

main {
    tree.a.score = 5;
    tree.b.data.score = 15;
    tree.c.other = 20;

    res << paths tree.* where !($..score > 10);

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
```

**Test logic**:
- Paths all direct children of `tree` (wildcard: `tree.*`)
- Filter for nodes where descendant `score` field is NOT greater than 10
- Uses NOT operator with recursive field search

**Expected output**:
```
tree.a
tree.c
```

**Why these results**:
- `tree.a` → `$..score` finds `tree.a.score = 5` → 5 > 10 is FALSE → NOT FALSE is TRUE ✓
- `tree.b` → `$..score` finds `tree.b.data.score = 15` → 15 > 10 is TRUE → NOT TRUE is FALSE → filtered out
- `tree.c` → `$..score` finds nothing (UNDEFINED) → UNDEFINED > 10 is FALSE → NOT FALSE is TRUE ✓

**What this tests**:
- NOT operator negation
- Recursive field search with `$..field` syntax
- Handling of UNDEFINED values in comparisons

**Critical insight**: When `$..score` doesn't find a field, it returns `Value.UNDEFINED_VALUE`, which evaluates as false in comparisons, so `!(false)` becomes `true`.

---

### Test 4: Mixed Field Access - Recursive + Direct

**File**: `test/paths/test_recursive_where_and_field.ol`

```jolie
include "console.iol"

main {
    tree.a.status = "active";
    tree.a.priority = 3;
    tree.b.status = "active";
    tree.b.priority = 8;
    tree.c.status = "inactive";
    tree.c.priority = 9;

    res << paths tree.* where $..status == "active" && $.priority > 5;

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
```

**Test logic**:
- Paths all direct children of `tree`
- Filter for nodes that have:
  - A descendant `status` field equal to "active" (`$..status`)
  - AND a direct child `priority` field greater than 5 (`$.priority`)

**Expected output**:
```
tree.b
```

**Why these results**:
- `tree.a`:
  - `$..status` finds `tree.a.status = "active"` → TRUE
  - `$.priority` accesses `tree.a.priority = 3` → 3 > 5 is FALSE
  - TRUE AND FALSE → filtered out

- `tree.b`:
  - `$..status` finds `tree.b.status = "active"` → TRUE
  - `$.priority` accesses `tree.b.priority = 8` → 8 > 5 is TRUE
  - TRUE AND TRUE → included ✓

- `tree.c`:
  - `$..status` finds `tree.c.status = "inactive"` → FALSE
  - (doesn't matter what priority is)
  - FALSE AND ? → filtered out

**What this tests**:
- Mixing `$..field` (recursive search) with `$.field` (direct access)
- Two different CurrentValueExpression types in same WHERE clause
- Both get bound to the same candidate node
- One searches recursively, one accesses directly

**Technical detail**: Both CurrentValueExpression instances point to the same `currentNode` (e.g., `tree.b`), but they navigate differently:
- `$..status` → searches entire subtree for "status"
- `$.priority` → directly accesses `tree.b.priority`

---

### Test 5: Complex Nested Expression

**File**: `test/paths/test_recursive_complex.ol`

```jolie
include "console.iol"

main {
    tree.a.value = 3;
    tree.b.data.value = 7;
    tree.c.nested.value = 15;
    tree.d.deep.value = 25;
    tree.e.value = 2;

    res << paths tree..value where $ > 0 && ($ < 10 || $ > 20);

    i = 0;
    while( i < #res.results ) {
        println@Console(res.results[i])();
        i++
    }
}
```

**Test logic**:
- Find all `value` fields at any depth
- Filter for values that are:
  - Greater than 0 AND
  - (Less than 10 OR Greater than 20)
- Uses nested parentheses with mixed operators

**Expected output**:
```
tree.e.value
tree.d.deep.value
tree.b.data.value
tree.a.value
```

**Why these results**:
- `tree.a.value = 3`:
  - 3 > 0 → TRUE
  - (3 < 10 || 3 > 20) → (TRUE || FALSE) → TRUE
  - TRUE AND TRUE → included ✓

- `tree.b.data.value = 7`:
  - 7 > 0 → TRUE
  - (7 < 10 || 7 > 20) → (TRUE || FALSE) → TRUE
  - TRUE AND TRUE → included ✓

- `tree.c.nested.value = 15`:
  - 15 > 0 → TRUE
  - (15 < 10 || 15 > 20) → (FALSE || FALSE) → FALSE
  - TRUE AND FALSE → filtered out

- `tree.d.deep.value = 25`:
  - 25 > 0 → TRUE
  - (25 < 10 || 25 > 20) → (FALSE || TRUE) → TRUE
  - TRUE AND TRUE → included ✓

- `tree.e.value = 2`:
  - 2 > 0 → TRUE
  - (2 < 10 || 2 > 20) → (TRUE || FALSE) → TRUE
  - TRUE AND TRUE → included ✓

**What this tests**:
- Deeply nested expression tree (3 levels)
- Parentheses create nested OR inside AND
- Three `$` references that all need binding
- Parser correctly respects operator precedence

**Expression tree structure**:
```
AndCondition
├─ CompareCondition ($ > 0)
│   └─ CurrentValueExpression  ← CVE #1
└─ OrCondition (from parentheses)
    ├─ CompareCondition ($ < 10)
    │   └─ CurrentValueExpression  ← CVE #2
    └─ CompareCondition ($ > 20)
        └─ CurrentValueExpression  ← CVE #3
```

All three CVE instances get collected and bound.

---

### Test Results Summary

**Test runner**: `test/paths/run_native_tests.py`

Updated to include new tests:

```python
tests = [
    # ... existing 12 tests ...
    ("test_recursive_and.ol", ["tree.d.value", "tree.b.data.value"]),
    ("test_recursive_or.ol", ["tree.c.nested.value", "tree.a.value"]),
    ("test_recursive_not.ol", ["tree.a", "tree.c"]),
    ("test_recursive_where_and_field.ol", ["tree.b"]),
    ("test_recursive_complex.ol", ["tree.e.value", "tree.d.deep.value", "tree.b.data.value", "tree.a.value"]),
]
```

**Final results**:
```
✓ test_native_wildcard.ol
✓ test_native_simple_value.ol
✓ test_native_greater_than.ol
✓ test_native_string_match.ol
✓ test_native_not_equal.ol
✓ test_select_single.ol
✓ test_select_single_no_match.ol
✓ test_dollar_field.ol
✓ test_dollar_nested_field.ol
✓ test_grandchildren.ol
✓ test_recursive_field.ol
✓ test_recursive_where.ol
✓ test_recursive_and.ol          ← NEW
✓ test_recursive_or.ol           ← NEW
✓ test_recursive_not.ol          ← NEW
✓ test_recursive_where_and_field.ol  ← NEW
✓ test_recursive_complex.ol      ← NEW

17/17 passed
```

---

## Complete File Inventory

### Files Modified (5)

#### 1. AndCondition.java
**File**: `jolie/src/main/java/jolie/runtime/expression/AndCondition.java`

**Lines changed**: 1
**Line number**: 38
**Change**: Made `children` field public

```java
// Before:
private final Expression[] children;

// After:
public final Expression[] children;
```

**Why necessary**: Allow `findAllCurrentValueExpressions()` to access child expressions for tree traversal.

**Classification**: CRITICAL - Without this, can't traverse AND expressions

---

#### 2. OrCondition.java
**File**: `jolie/src/main/java/jolie/runtime/expression/OrCondition.java`

**Lines changed**: 1
**Line number**: 30
**Change**: Made `children` field public

```java
// Before:
final private Expression[] children;

// After:
public final Expression[] children;
```

**Why necessary**: Allow traversal of OR expression children.

**Classification**: CRITICAL - Without this, can't traverse OR expressions

---

#### 3. NotExpression.java
**File**: `jolie/src/main/java/jolie/runtime/expression/NotExpression.java`

**Lines changed**: 1
**Line number**: 29
**Change**: Made `expression` field public

```java
// Before:
private final Expression expression;

// After:
public final Expression expression;
```

**Why necessary**: Allow traversal through NOT negation.

**Classification**: CRITICAL - Without this, can't traverse NOT expressions

---

#### 4. PathsExpression.java
**File**: `jolie/src/main/java/jolie/runtime/expression/PathsExpression.java`

**Lines changed**: ~50
**Line numbers**: 55-119

**Changes**:
1. **Lines 55-57**: Change variable declaration from single to list
   ```java
   // Before:
   CurrentValueExpression currentValueExpr = findCurrentValueExpression(whereExpression);

   // After:
   List<CurrentValueExpression> currentValueExprs = new ArrayList<>();
   findAllCurrentValueExpressions(whereExpression, currentValueExprs);
   ```

2. **Lines 64-67**: Change binding loop to bind all instances
   ```java
   // Before:
   if (currentValueExpr != null) {
       currentValueExpr.setCurrentNode(candidateValue);
   }

   // After:
   for (CurrentValueExpression expr : currentValueExprs) {
       expr.setCurrentNode(candidateValue);
   }
   ```

3. **Lines 83-119**: Replace entire `findCurrentValueExpression()` method with `findAllCurrentValueExpressions()`
   - Changed return type: `CurrentValueExpression` → `void`
   - Added output parameter: `List<CurrentValueExpression> result`
   - Added recursive traversal for `AndCondition`, `OrCondition`, `NotExpression`
   - Changed from "find first" to "collect all" semantics

**Why necessary**: This is the core WHERE evaluation logic for PATHS expressions (with `<<` operator).

**Classification**: ABSOLUTELY CRITICAL

---

#### 5. PathsProcess.java
**File**: `jolie/src/main/java/jolie/process/PathsProcess.java`

**Lines changed**: ~50
**Line numbers**: 63-121

**Changes**: Identical to `PathsExpression.java`:
1. Lines 63-64: Change to list collection
2. Lines 72-74: Bind all instances loop
3. Lines 85-121: Replace traversal method

**Why necessary**: This is the WHERE evaluation logic for PATHS statements (without `<<`).

**Classification**: ABSOLUTELY CRITICAL

**Note**: The duplication between PathsExpression and PathsProcess is architectural - Jolie separates expressions (produce values) from processes (cause effects).

---

### Test Files Created (5)

#### 6. test_recursive_and.ol
**Lines**: 16
**Purpose**: Test AND operator with range filtering

#### 7. test_recursive_or.ol
**Lines**: 16
**Purpose**: Test OR operator with alternative conditions

#### 8. test_recursive_not.ol
**Lines**: 16
**Purpose**: Test NOT operator with recursive field

#### 9. test_recursive_where_and_field.ol
**Lines**: 17
**Purpose**: Test mixing recursive and direct field access

#### 10. test_recursive_complex.ol
**Lines**: 17
**Purpose**: Test nested parentheses with mixed operators

---

### Test Runner Modified (1)

#### 11. run_native_tests.py
**File**: `test/paths/run_native_tests.py`

**Lines changed**: 5
**Lines**: 23-27

**Change**: Added 5 new test cases to the test list

```python
# Added:
("test_recursive_and.ol", ["tree.d.value", "tree.b.data.value"]),
("test_recursive_or.ol", ["tree.c.nested.value", "tree.a.value"]),
("test_recursive_not.ol", ["tree.a", "tree.c"]),
("test_recursive_where_and_field.ol", ["tree.b"]),
("test_recursive_complex.ol", ["tree.e.value", "tree.d.deep.value", "tree.b.data.value", "tree.a.value"]),
```

**Why necessary**: Integrate new tests into automated test suite.

---

### Total Impact

- **Source files modified**: 5
  - **Critical runtime files**: 2 (PathsExpression.java, PathsProcess.java)
  - **Critical operator files**: 3 (AndCondition.java, OrCondition.java, NotExpression.java)
- **Test files created**: 5
- **Test files modified**: 1
- **Total files touched**: 11
- **Total lines changed in source**: ~103 lines

**Breakdown by type**:
- Field visibility changes: 3 files, 3 lines (trivial but essential)
- Binding logic rewrite: 2 files, ~100 lines (complex and critical)

**Impact assessment**:
- **Low invasiveness**: Only 5 source files modified
- **High impact**: Enables all boolean operators in WHERE clauses
- **No interface overhead**: No visitor pattern changes needed (boolean operators already existed)
- **100% critical changes**: Every change is absolutely necessary

---

## Lessons Learned

### 1. Mutable State in Functional Trees

**Problem**: Expression trees are generally immutable (functional), but `CurrentValueExpression` has mutable state (`currentNode`).

**Solution**: The mutable state is acceptable here because:
- It's set immediately before evaluation
- It's thread-local (each ExecutionThread has its own expression tree instances via `cloneExpression()`)
- It's an optimization over passing the current value through the entire evaluation chain

**Lesson**: Sometimes mutable state is the right design choice, even in primarily functional architectures.

---

### 2. Shallow vs Deep Tree Traversal

**Original mistake**: Assuming `findCurrentValueExpression()` would "somehow" find all instances.

**Reality**: Trees require explicit recursive traversal. There's no magic.

**Solution pattern**:
```java
// Shallow (buggy):
if (expr instanceof Container) {
    return findInChildren();  // Only checks direct children
}

// Deep (correct):
if (expr instanceof Container) {
    for (child in expr.children) {
        recurse(child);  // Recursively traverse entire subtree
    }
}
```

**Lesson**: When working with trees, always think explicitly about traversal strategy:
- Depth-first vs breadth-first
- Shallow (one level) vs deep (all levels)
- First match vs all matches

---

### 3. Return Value vs Output Parameter

**Original approach**: Return first match
```java
private CurrentValueExpression find(Expression expr) {
    if (found) return it;  // Stops search
}
```

**Problem**: Can't return multiple values.

**Solution**: Output parameter pattern
```java
private void find(Expression expr, List<CurrentValueExpression> result) {
    if (found) result.add(it);  // Continue search
}
```

**Lesson**: Output parameters are appropriate when:
- You need to collect multiple values
- You need to continue traversal after finding matches
- The collection is the primary output (not secondary)

**Alternative considered**: Return List directly
```java
private List<CVE> find(Expression expr) {
    List<CVE> result = new ArrayList<>();
    // ... collect ...
    return result;
}
```

**Why output parameter is better**:
- Fewer allocations (one list shared across all recursive calls)
- More efficient for deep trees
- Clear separation of "traversal logic" from "result accumulation"

---

### 4. Field Visibility Trade-offs

**Options considered**:
1. **Add getter methods** (object-oriented purity)
   ```java
   public Expression[] getChildren() { return children; }
   ```

2. **Make fields public** (pragmatic simplicity)
   ```java
   public final Expression[] children;
   ```

**Decision**: Made fields public because:
- Fields are `final` - can't be modified, so encapsulation less important
- No validation logic needed - fields are set in constructor
- Simpler than adding boilerplate getters
- More performant (direct field access vs method call)
- Common pattern in Jolie's expression classes

**Lesson**: Encapsulation is a means to an end, not an end itself. For immutable data structures, direct field access can be acceptable.

---

### 5. Testing Strategy for Tree Traversal

**Key insight**: Test cases should exercise different tree shapes:

1. **Linear (AND chain)**: `$ > 5 && $ < 20`
   - Tests two operands at same level

2. **Linear (OR chain)**: `$ < 5 || $ > 20`
   - Tests short-circuit behavior

3. **Nested (mixed)**: `$ > 0 && ($ < 10 || $ > 20)`
   - Tests 3+ levels of nesting
   - Tests parentheses creating sub-trees

4. **Unary (NOT)**: `!($..score > 10)`
   - Tests traversal through unary operators

5. **Heterogeneous**: `$..status == "active" && $.priority > 5`
   - Tests different CurrentValueExpression types
   - Tests multiple navigation strategies

**Lesson**: When testing tree algorithms, think about tree **topology**, not just input values:
- Depth (how nested?)
- Width (how many children?)
- Shape (balanced vs skewed?)
- Node types (homogeneous vs heterogeneous?)

---

### 6. The Stack-Based DFS Pattern

**Observation**: Our recursive `findAllCurrentValueExpressions()` is actually a **stack-based DFS** - the call stack is our stack.

**Iterative equivalent** (not implemented, but educational):
```java
private void findAllCurrentValueExpressions(Expression root, List<CVE> result) {
    Stack<Expression> stack = new Stack<>();
    stack.push(root);

    while (!stack.isEmpty()) {
        Expression expr = stack.pop();

        if (expr instanceof CurrentValueExpression) {
            result.add((CVE) expr);
        } else if (expr instanceof CompareCondition) {
            CompareCondition cmp = (CompareCondition) expr;
            stack.push(cmp.rightExpression());
            stack.push(cmp.leftExpression());
        } else if (expr instanceof AndCondition) {
            AndCondition and = (AndCondition) expr;
            for (int i = and.children.length - 1; i >= 0; i--) {
                stack.push(and.children[i]);
            }
        }
        // ... etc
    }
}
```

**Why recursive is better here**:
- Simpler code
- Java handles stack management
- Expression trees are not deep enough to cause stack overflow
- Recursive form is more readable

**Lesson**: Recursion is elegant for tree traversal when:
- Trees are bounded depth (< ~1000 levels)
- Code clarity matters
- No tail-call optimization needed

---

### 7. Error Messages Matter

**Original error**: `"$ not bound"`

**Problem**: Doesn't tell you **which** `$` or **where** in the expression.

**Potential improvement** (not implemented):
```java
public Value evaluate() {
    if (currentNode == null) {
        throw new IllegalStateException(
            "$ not bound at " + context.sourceName() + ":" + context.line()
        );
    }
    return currentNode;
}
```

**Lesson**: In large expression trees, location information in errors is invaluable. The more complex the feature, the more important good error messages become.

---

### 8. Incremental Implementation

**What worked**:
1. ✅ First implement simple WHERE (`$ == 5`)
2. ✅ Then add recursive fields (`$..field`)
3. ✅ Then add boolean operators (`$ > 5 && $ < 20`)
4. ✅ Each step builds on previous

**What would have failed**:
Trying to implement everything at once - too many moving parts, impossible to debug.

**Lesson**: When implementing complex features:
- Break into phases
- Ensure each phase works before moving on
- Write tests for each phase
- Each phase should be independently valuable

**Our phases**:
- **Phase 1**: Native WHERE with `$` (replaced ANTLR)
- **Phase 2**: Native PATHS paths with `var.*` (replaced ANTLR)
- **Phase 3**: Recursive field descent `..field` (JSONPath-like)
- **Phase 4**: Boolean operators (current work)
- **Phase 5** (future): Complete ANTLR removal

Each phase is usable on its own, even if following phases don't happen.

---

## Conclusion

Adding boolean operator support to PATHS WHERE clauses required:

1. **Field visibility changes**: Made `children`/`expression` fields public in 3 operator classes
2. **Traversal algorithm rewrite**: Replaced shallow single-instance search with deep recursive collection
3. **Binding loop update**: Bind all collected CurrentValueExpression instances instead of just one
4. **Comprehensive testing**: 5 new tests covering AND, OR, NOT, mixed operations, and nested expressions

**Total changes**:
- **5 source files modified** (~103 lines changed)
- **5 test files created** (~82 lines total)
- **1 test runner updated** (5 lines added)
- **Zero interface overhead** (no visitor pattern changes needed)

**Key insight**: The bug was subtle but the fix was straightforward once identified. The original implementation was "almost correct" - it handled simple cases perfectly but failed on complex boolean expressions because it only bound the first `$` reference.

**Impact**: Users can now write complex filtering logic using familiar boolean operators:

```jolie
// All of these now work:
result << paths tree.* where $ > 5 && $ < 20
result << paths tree.* where $ == "active" || $ == "pending"
result << paths tree.* where !($..hidden == true)
result << paths tree.* where $..score > 50 && $.verified == true
result << paths tree.* where ($ < 10 || $ > 90) && $ != 50
```

**Next steps**: The boolean operator infrastructure is complete. Future work will focus on:
- Performance optimization (short-circuit evaluation already works)
- More complex WHERE features (e.g., `has`, `in`, regex matching)
- Complete ANTLR removal from PATHS path traversal
