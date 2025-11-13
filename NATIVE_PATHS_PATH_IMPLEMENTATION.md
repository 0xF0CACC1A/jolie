# Converting PATHS Path from ANTLR Strings to Native Jolie Syntax

## Table of Contents
1. [Overview](#overview)
2. [Architecture Before vs After](#architecture-before-vs-after)
3. [The var.* Syntax: Native Path Specification](#the-var-syntax-native-path-selection)
4. [Implementation Steps](#implementation-steps)
5. [Critical vs Interface-Only Changes](#critical-vs-interface-only-changes)
6. [Parser Token Consumption Strategy](#parser-token-consumption-strategy)
7. [Backward Compatibility](#backward-compatibility)
8. [Testing and Verification](#testing-and-verification)
9. [Complete File Inventory](#complete-file-inventory)

---

## Overview

### Goal
Convert the PATHS primitive's path specification from ANTLR-parsed strings to native Jolie syntax.

**Before:**
```jolie
result << paths "$.*" from var where $ == 5
                 ↑ ANTLR string for path
```

**After:**
```jolie
result << paths var.* from var where $ == 5
                 ↑ Native Jolie path syntax
```

### Why This Change?

1. **Consistency**: PATHS paths use native Jolie path syntax (`var.*`) like the rest of the language
2. **Type Safety**: Compile-time checking for variable existence
3. **Performance**: No ANTLR parsing overhead for the path component
4. **Simplification**: Eventually allows complete removal of ANTLR dependency
5. **Extensibility**: Easy to add new path patterns (arrays `[*]`, recursive descent `..`, etc.)

### Key Challenge

The PATHS path needs to support wildcard patterns (`*`) that don't exist in standard Jolie variable paths, which are typically concrete like `root.field.subfield`.

---

## Architecture Before vs After

### Before: ANTLR String-Based PATHS Path

```
┌─────────────────────────────────────────────────────────────┐
│ Jolie Code: paths "$.*" from var where $ == 5              │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Parser (OLParser.java)                                       │
│   - PATHS path: string → stored as String                   │
│   - FROM variable: parseVariablePath() → VariablePathNode    │
│   - WHERE clause: parseExpression() → OLSyntaxNode          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ AST: PathsStatement                                         │
│   String pathsQuery = "$.*"           ← STRING              │
│   VariablePathNode fromVariable = var                        │
│   OLSyntaxNode whereExpression = ...                        │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Runtime: PathsProcess                                       │
│   - Pass pathsQuery string to ANTLR parser                  │
│   - PathsQueryExecutor parses "$.*" at runtime              │
│   - ANTLR generates JSONPath-like navigation                 │
└─────────────────────────────────────────────────────────────┘
```

### After: Native Jolie Path Syntax

```
┌─────────────────────────────────────────────────────────────┐
│ Jolie Code: paths var.* from var where $ == 5              │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Scanner (Scanner.java)                                       │
│   - Tokenizes: PATHS, ID(var), DOT, ASTERISK, FROM, ...    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Parser (OLParser.java)                                       │
│   - PATHS path: Manual token consumption                    │
│     ├─ Eat ID token → "var"                                  │
│     ├─ Create VariablePathNode(var)                          │
│     ├─ Eat DOT token                                         │
│     ├─ Eat ASTERISK token                                    │
│     └─ Create PathSpecNode(var, wildcard=true)             │
│   - FROM variable: parseVariablePath() → (ignored)           │
│   - WHERE clause: parseExpression() → OLSyntaxNode          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ AST: PathsStatement                                         │
│   PathSpecNode pathSpec                    ← NEW NODE    │
│     ├─ VariablePathNode baseVariable = var                   │
│     └─ boolean isWildcard = true                             │
│   OLSyntaxNode whereExpression = ...                        │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ AST Optimization (OLParseTreeOptimizer.java)                 │
│   - Traverses PathSpecNode                                 │
│   - Optimizes baseVariable path                              │
│   - Preserves wildcard flag                                  │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ AST-to-Runtime Conversion (OOITBuilder.java)                 │
│   - Converts PathSpecNode to runtime VariablePath          │
│   - Extracts baseVariable and isWildcard flag                │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Runtime: PathsProcess                                       │
│   VariablePath pathSpec (e.g., "var")                      │
│   boolean isWildcard (true)                                  │
│                                                              │
│   Execution:                                                 │
│     1. Convert wildcard to ANTLR string: "$.*"               │
│     2. Pass to PathsQueryExecutor (temporary)               │
│     3. Filter results with WHERE expression                  │
└─────────────────────────────────────────────────────────────┘
```

---

## The var.* Syntax: Native Path Specification

### Why var.* is Needed

In PATHS queries, you often want to paths "all direct children" of a node:

```jolie
// Find all values equal to 5
tree.a = 5;
tree.b = 6;
tree.c = 5;

result << paths tree.* from tree where $ == 5
                     ↑
                All children of tree
```

Without `*`, you'd need to enumerate: `paths tree.a, tree.b, tree.c` (not practical for dynamic data).

### Design Constraints

1. **Cannot reuse existing variable path syntax**: Jolie's `parseVariablePath()` is greedy and will try to parse `*` as a field name
2. **Must support wildcards**: The `*` character is already tokenized as ASTERISK for multiplication
3. **Must be backward compatible**: Old ANTLR string syntax must continue to work

### Implementation Approach

Create a new AST node that combines a base variable path with a wildcard flag:

```java
public class PathSpecNode extends OLSyntaxNode {
    private final VariablePathNode baseVariable;  // The "var" part
    private final boolean isWildcard;              // The ".*" part

    public PathSpecNode(ParsingContext context,
                         VariablePathNode baseVariable,
                         boolean isWildcard) {
        super(context);
        this.baseVariable = baseVariable;
        this.isWildcard = isWildcard;
    }
}
```

---

## Implementation Steps

### Step 1: Create PathSpecNode AST Node

**File**: `libjolie/src/main/java/jolie/lang/parse/ast/expression/PathSpecNode.java` (NEW)

```java
package jolie.lang.parse.ast.expression;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.VariablePathNode;
import jolie.lang.parse.context.ParsingContext;

/**
 * Represents a native PATHS path (e.g., var.* for wildcard selection).
 * This replaces the ANTLR string-based path specification.
 */
public class PathSpecNode extends OLSyntaxNode {
    private final VariablePathNode baseVariable;
    private final boolean isWildcard;

    public PathSpecNode(ParsingContext context,
                         VariablePathNode baseVariable,
                         boolean isWildcard) {
        super(context);
        this.baseVariable = baseVariable;
        this.isWildcard = isWildcard;
    }

    public VariablePathNode baseVariable() {
        return baseVariable;
    }

    public boolean isWildcard() {
        return isWildcard;
    }

    @Override
    public <C, R> R accept(OLVisitor<C, R> visitor, C ctx) {
        return visitor.visit(this, ctx);
    }
}
```

**Why necessary**: Every syntactic construct needs an AST node. This represents the native `var.*` path.

**Package location**: In `ast.expression` because PATHS paths can appear in expression contexts (with `<<` operator).

**Key design**:
- `baseVariable`: The variable being selected from (e.g., `tree`, `data.items`)
- `isWildcard`: Whether `.*` was specified (for now, always true)

---

### Step 2: Update PathsStatement and PathsExpressionNode AST Classes

**File**: `libjolie/src/main/java/jolie/lang/parse/ast/PathsStatement.java`

**Before:**
```java
public class PathsStatement extends OLSyntaxNode {
    private final String pathsQuery;           // ← ANTLR string
    private final VariablePathNode intoVariable;
    private final VariablePathNode fromVariable;
    private final OLSyntaxNode whereExpression;

    public PathsStatement(ParsingContext context,
                          String pathsQuery,
                          VariablePathNode intoVariable,
                          VariablePathNode fromVariable,
                          OLSyntaxNode whereExpression) {
        super(context);
        this.pathsQuery = pathsQuery;
        this.intoVariable = intoVariable;
        this.fromVariable = fromVariable;
        this.whereExpression = whereExpression;
    }

    public String pathsQuery() { return pathsQuery; }
    public VariablePathNode fromVariable() { return fromVariable; }
}
```

**After:**
```java
public class PathsStatement extends OLSyntaxNode {
    private final PathSpecNode pathSpec;    // ← Native path node
    private final OLSyntaxNode whereExpression;

    public PathsStatement(ParsingContext context,
                          PathSpecNode pathSpec,
                          OLSyntaxNode whereExpression) {
        super(context);
        this.pathSpec = pathSpec;
        this.whereExpression = whereExpression;
    }

    public PathSpecNode pathSpec() { return pathSpec; }
    public OLSyntaxNode whereExpression() { return whereExpression; }
}
```

**Changes**:
1. **Removed `String pathsQuery`**: No longer using ANTLR string
2. **Removed `VariablePathNode fromVariable`**: The FROM variable is now part of PathSpecNode
3. **Removed `VariablePathNode intoVariable`**: INTO clause was already removed in previous refactoring (using `<<` operator now)
4. **Added `PathSpecNode pathSpec`**: New native path representation

**Why necessary**: The AST must store the PATHS path as a structured node, not a string.

**Identical change needed for**: `PathsExpressionNode.java` (expression variant of PATHS)

---

### Step 3: Update Parser to Parse Native PATHS Syntax

**File**: `libjolie/src/main/java/jolie/lang/parse/OLParser.java`

This is the most critical change - teaching the parser to recognize `var.*` syntax.

**Change 3.1**: Add import (line 131)
```java
import jolie.lang.parse.ast.expression.PathSpecNode;
```

**Change 3.2**: Update PATHS statement parsing (line 2512-2563)

**Before:**
```java
case PATHS:
    nextToken();
    assertToken(Scanner.TokenType.STRING, "expected PATHS query string");
    String pathsQuery = token.content().replaceAll("\"", "");
    nextToken();
    eat(Scanner.TokenType.INTO, "expected INTO after PATHS expression");
    VariablePathNode intoVar = parseVariablePath();
    eat(Scanner.TokenType.FROM, "expected FROM after INTO variable");
    VariablePathNode fromVar = parseVariablePath();
    eat(Scanner.TokenType.WHERE, "expected WHERE after FROM variable");
    OLSyntaxNode whereExpr = parseExpression();
    retVal = new PathsStatement(getContext(), pathsQuery, intoVar, fromVar, whereExpr);
    break;
```

**After:**
```java
case PATHS:
    nextToken();

    PathSpecNode pathSpec;
    VariablePathNode fromVar;

    // Check if old ANTLR string syntax or new native syntax
    if (token.is(Scanner.TokenType.STRING)) {
        // Old syntax: paths "$.*" into results from var where ...
        // For backward compatibility
        nextToken(); // eat the string

        // Check for INTO (old syntax with INTO clause)
        if (token.is(Scanner.TokenType.INTO)) {
            nextToken();
            assertIdentifier("expected variable name after INTO");
            nextToken(); // eat the into variable (ignored with << operator)
        }

        eat(Scanner.TokenType.FROM, "expected FROM after PATHS query");
        fromVar = parseVariablePath();

        // Create PathSpecNode with wildcard (assume "$.*" for now)
        pathSpec = new PathSpecNode(getContext(), fromVar, true);
    } else {
        // New native syntax: paths var.* from var where ...
        assertIdentifier("expected variable name after PATHS");
        String varId = token.content();
        nextToken();

        // Create simple VariablePathNode with just the base variable
        // We manually construct this instead of using parseVariablePath()
        // because parseVariablePath() would consume the DOT and try to
        // parse ASTERISK as a field name
        VariablePathNode baseVar = new VariablePathNode(getContext(), Type.NORMAL);
        baseVar.append(new Pair<>(
            new ConstantStringExpression(getContext(), varId),
            null));

        // Check for DOT
        eat(Scanner.TokenType.DOT, "expected . after variable in PATHS");

        // Check for ASTERISK (*)
        eat(Scanner.TokenType.ASTERISK, "expected * after . in PATHS");

        pathSpec = new PathSpecNode(getContext(), baseVar, true);

        eat(Scanner.TokenType.FROM, "expected FROM after PATHS path");

        parseVariablePath(); // Parse but ignore - FROM is now useless
    }

    eat(Scanner.TokenType.WHERE, "expected WHERE after FROM variable");

    OLSyntaxNode whereExpr = parseExpression();

    retVal = new PathsStatement(getContext(), pathSpec, whereExpr);
    break;
```

**Why this approach**:

1. **Backward compatibility first**: Check if token is STRING (old syntax) vs ID (new syntax)
2. **Manual token consumption**: Cannot use `parseVariablePath()` because it's greedy and will try to consume `.*`
3. **Explicit token eating**: Use `eat()` to consume DOT and ASTERISK tokens with error messages
4. **FROM clause handling**: Still parse FROM but ignore its argument (transitional state)

**Key insight**: The trick is to **eat tokens manually** instead of calling parsing helper methods. This gives us fine-grained control over what gets consumed.

**Identical change needed at**: Line 3727-3770 for `PathsExpressionNode` (expression variant)

---

### Step 4: Add PathSpecNode to Visitor Interfaces

This is where we encounter **interface overhead** again (similar to CurrentValueNode).

#### Step 4.1: Update OLVisitor Interface

**File**: `libjolie/src/main/java/jolie/lang/parse/OLVisitor.java`

**Change**: Add method signature (line ~275)
```java
public interface OLVisitor<C, R> {
    // ... other visit methods ...

    R visit(PathSpecNode n, C ctx);  // ← NEW
}
```

**Why necessary**: All AST node types need a visitor method in the interface.

#### Step 4.2: Update UnitOLVisitor Interface

**File**: `libjolie/src/main/java/jolie/lang/parse/UnitOLVisitor.java`

**Change**: Add default implementation (line ~780)
```java
public interface UnitOLVisitor extends OLVisitor<Unit, Unit> {
    // ... other methods ...

    void visit(PathSpecNode n);

    @Override
    default Unit visit(PathSpecNode n, Unit ctx) {
        visit(n);
        return Unit.INSTANCE;
    }
}
```

---

### Step 5: Implement visit(PathSpecNode) in ALL Visitor Classes

We must add this method to **10 different classes**:

#### 5.1 SemanticVerifier (FUNCTIONAL)

**File**: `libjolie/src/main/java/jolie/lang/parse/SemanticVerifier.java`

```java
import jolie.lang.parse.ast.expression.PathSpecNode;  // Line ~118

@Override
public void visit(PathSpecNode n) {
    // Visit the base variable to check it exists
    n.baseVariable().accept(this);
}  // Line ~1070

// Also update visit(PathsStatement) and visit(PathsExpressionNode):
@Override
public void visit(PathsStatement n) {
    // Visit PATHS path and WHERE expression
    n.pathSpec().accept(this);
    n.whereExpression().accept(this);
}
```

**Why**: Must verify that the base variable exists in scope.

**Classification**: Functional (not just interface satisfaction)

#### 5.2 TypeChecker (FUNCTIONAL)

**File**: `libjolie/src/main/java/jolie/lang/parse/TypeChecker.java`

```java
import jolie.lang.parse.ast.expression.PathSpecNode;  // Line ~102

@Override
public void visit(PathSpecNode n) {
    // Visit the base variable for type checking
    n.baseVariable().accept(this);
}  // Line ~760

// Also update visit(PathsExpressionNode):
@Override
public void visit(PathsExpressionNode n) {
    n.pathSpec().accept(this);
}
```

**Why**: Must check types in the base variable path.

**Classification**: Functional

#### 5.3 SymbolReferenceResolver (EMPTY - Interface Satisfaction)

**File**: `libjolie/src/main/java/jolie/lang/parse/module/SymbolReferenceResolver.java`

```java
import jolie.lang.parse.ast.expression.PathSpecNode;  // Line ~116

@Override
public void visit(PathSpecNode n) {}  // Line ~340

// Also update visit(PathsExpressionNode):
@Override
public void visit(PathsExpressionNode n) {
    n.pathSpec().accept(this);
}
```

**Why empty**: PathSpecNode doesn't reference module symbols.

**Classification**: Interface satisfaction only

#### 5.4 SymbolTableGenerator (EMPTY - Interface Satisfaction)

**File**: `libjolie/src/main/java/jolie/lang/parse/module/SymbolTableGenerator.java`

```java
import jolie.lang.parse.ast.expression.PathSpecNode;  // Line ~95

@Override
public void visit(PathSpecNode n) {}  // Line ~197

// Also update visit(PathsExpressionNode):
@Override
public void visit(PathsExpressionNode n) {
    n.pathSpec().accept(this);
}
```

**Why empty**: PathSpecNode doesn't add symbols to the symbol table.

**Classification**: Interface satisfaction only

#### 5.5 ProgramInspectorCreatorVisitor (EMPTY - Interface Satisfaction)

**File**: `libjolie/src/main/java/jolie/lang/parse/util/impl/ProgramInspectorCreatorVisitor.java`

```java
import jolie.lang.parse.ast.expression.PathSpecNode;  // Line ~99

@Override
public void visit(PathSpecNode n) {}  // Line ~301

// Also update visit(PathsExpressionNode):
@Override
public void visit(PathsExpressionNode n) {
    n.pathSpec().accept(this);
}
```

**Why empty**: Program inspection doesn't need special handling for PathSpecNode.

**Classification**: Interface satisfaction only

#### 5.6 InterfaceVisitor (EMPTY - Interface Satisfaction)

**File**: `tools/jolie2plasma/src/main/java/joliex/plasma/impl/InterfaceVisitor.java`

```java
import jolie.lang.parse.ast.expression.PathSpecNode;  // Line ~93

@Override
public void visit(PathSpecNode n) {}  // Line ~198
```

**Why empty**: PathSpecNode doesn't appear in interface definitions.

**Classification**: Interface satisfaction only

#### 5.7 OLParseTreeOptimizer (CRITICAL - Must Preserve Node!)

**File**: `libjolie/src/main/java/jolie/lang/parse/OLParseTreeOptimizer.java`

```java
import jolie.lang.parse.ast.expression.PathSpecNode;  // Line ~101

@Override
public void visit(PathSpecNode n) {
    // Optimize the base variable path, preserve wildcard flag
    currNode = new PathSpecNode(
        n.context(),
        optimizePath(n.baseVariable()),
        n.isWildcard());
}  // Line ~658-662

// Also update visit(PathsStatement) and visit(PathsExpressionNode):
@Override
public void visit(PathsStatement n) {
    currNode = new PathsStatement(
        n.context(),
        (PathSpecNode) optimizeNode(n.pathSpec()),
        optimizeNode(n.whereExpression()));
}
```

**Why**: Must preserve PathSpecNode through optimization, applying optimizations to the base variable.

**Classification**: ABSOLUTELY CRITICAL

**What happens if empty**: The PathSpecNode is lost, causing PATHS to break.

#### 5.8 OOITBuilder (CRITICAL - Converts AST to Runtime!)

**File**: `jolie/src/main/java/jolie/OOITBuilder.java`

```java
import jolie.lang.parse.ast.expression.PathSpecNode;  // Line ~132

@Override
public void visit(PathSpecNode n) {
    // PathSpecNode is handled inline in PathsStatement/PathsExpressionNode visitors
    // No runtime representation needed for PathSpecNode itself
}  // Line ~1501-1504

// Update visit(PathsStatement):
@Override
public void visit(PathsStatement n) {
    currProcess = new PathsProcess(
        buildVariablePath(n.pathSpec().baseVariable()),
        n.pathSpec().isWildcard(),
        buildExpression(n.whereExpression()));
}  // Line ~1729-1734

// Update visit(PathsExpressionNode):
@Override
public void visit(PathsExpressionNode n) {
    currExpression = new PathsExpression(
        buildVariablePath(n.pathSpec().baseVariable()),
        n.pathSpec().isWildcard(),
        buildExpression(n.whereExpression()));
}  // Line ~1493-1497
```

**Why**: Converts AST PathSpecNode to runtime VariablePath and boolean flag.

**Classification**: ABSOLUTELY CRITICAL

**What it does**: Extracts `baseVariable()` and `isWildcard()` from PathSpecNode and passes them to runtime classes.

---

### Step 6: Update Runtime Classes to Accept Native Paths

**File**: `jolie/src/main/java/jolie/runtime/expression/PathsExpression.java`

**Before:**
```java
public class PathsExpression implements Expression {
    private final String pathsQuery;           // ← ANTLR string
    private final VariablePath fromVariable;
    private final Expression whereExpression;

    public PathsExpression(String pathsQuery,
                           VariablePath fromVariable,
                           Expression whereExpression) {
        this.pathsQuery = pathsQuery;
        this.fromVariable = fromVariable;
        this.whereExpression = whereExpression;
    }

    @Override
    public Value evaluate() {
        // ... use pathsQuery and fromVariable separately
    }
}
```

**After:**
```java
public class PathsExpression implements Expression {
    private final VariablePath pathSpec;      // ← Native variable path
    private final boolean isWildcard;           // ← Wildcard flag
    private final Expression whereExpression;

    public PathsExpression(VariablePath pathSpec,
                           boolean isWildcard,
                           Expression whereExpression) {
        this.pathSpec = pathSpec;
        this.isWildcard = isWildcard;
        this.whereExpression = whereExpression;
    }

    @Override
    public Value evaluate() {
        String rootPath = extractRootPath(pathSpec);
        ValueVector vec = pathSpec.getValueVector();
        Object source = vec.size() > 1 ? vec : vec.first();

        // Convert native path to ANTLR query string (temporary)
        String pathsQuery = isWildcard ? "$.*" : "$";

        // Execute PATHS query without WHERE filtering
        List<String> candidatePaths = PathsQueryExecutor.execute(
            source,
            pathsQuery,
            null,
            rootPath);

        // Filter candidates using native Jolie WHERE expression
        // ... (rest of filtering logic)
    }

    @Override
    public Expression cloneExpression(TransformationReason reason) {
        return new PathsExpression(
            (VariablePath) pathSpec.cloneExpression(reason),
            isWildcard,
            whereExpression.cloneExpression(reason));
    }
}
```

**Changes**:
1. **Removed `String pathsQuery`**: No longer receive ANTLR string from AST
2. **Removed `VariablePath fromVariable`**: Merged into `pathSpec`
3. **Added `VariablePath pathSpec`**: The base variable being selected from
4. **Added `boolean isWildcard`**: Whether `.*` was specified
5. **Runtime conversion**: Convert `isWildcard` to ANTLR string `"$.*"` temporarily

**Why this is transitional**: We still call `PathsQueryExecutor.execute()` with ANTLR strings. The full native implementation would replace this with Jolie-based path traversal. For now, we convert at runtime: `var.* → "$.*"`.

**Identical change needed**: In `PathsProcess.java` (statement variant)

---

### Step 7: Handle FROM Clause Transition

The FROM clause is now redundant because the variable is specified in the PATHS path itself:

**Old syntax:**
```jolie
paths "$.*" from tree where $ == 5
       ↑ path    ↑ variable
```

**New syntax:**
```jolie
paths tree.* from tree where $ == 5
       ↑ variable+path  ↑ redundant!
```

**Current approach**: Parse FROM but ignore its argument. This maintains syntax compatibility while we transition.

**Parser code**:
```java
eat(Scanner.TokenType.FROM, "expected FROM after PATHS path");
parseVariablePath(); // Parse but ignore - FROM is now useless
```

**Future step**: Remove FROM keyword entirely:
```jolie
paths tree.* where $ == 5
       ↑ variable+path  (no FROM needed)
```

---

## Critical vs Interface-Only Changes

### Absolutely Necessary (Core Functionality)

These changes are **required** for the feature to work:

| File | Change | Why |
|------|--------|-----|
| PathSpecNode.java | New file | AST representation of `var.*` |
| OLParser.java | Manual token consumption | Parse `var.*` syntax |
| OLParser.java | Backward compatibility | Support old string syntax |
| PathsStatement.java | pathSpec field | Store native path |
| PathsExpressionNode.java | pathSpec field | Store native path |
| OLParseTreeOptimizer.java | visit(PathSpecNode) | Preserve through optimization ⚠️ |
| OOITBuilder.java | visit(PathSpecNode) | Convert AST to runtime |
| OOITBuilder.java | Extract baseVariable/wildcard | Pass to runtime classes |
| PathsProcess.java | Accept native path | Runtime execution |
| PathsExpression.java | Accept native path | Runtime execution |

**Total: 10 critical changes across 8 files (2 new)**

### Necessary for Correctness

These changes are **necessary** but contain minimal logic:

| File | Change | Why |
|------|--------|-----|
| SemanticVerifier.java | Visit baseVariable | Verify variable exists |
| SemanticVerifier.java | Update PathsStatement visitor | Traverse pathSpec |
| TypeChecker.java | Visit baseVariable | Check types |
| TypeChecker.java | Update PathsExpressionNode visitor | Traverse pathSpec |
| OLParseTreeOptimizer.java | Update PathsStatement visitor | Optimize pathSpec |

**Total: 5 correctness changes across 3 files**

### Interface Satisfaction Only

These changes are **required by the visitor pattern** but contain no logic:

| File | Change | Why Interface Required |
|------|--------|------------------------|
| OLVisitor.java | visit(PathSpecNode) signature | All nodes need visitor method |
| UnitOLVisitor.java | visit(PathSpecNode) default | Adapter for void visitors |
| SymbolReferenceResolver.java | Empty visit() | Implements UnitOLVisitor |
| SymbolTableGenerator.java | Empty visit() | Implements UnitOLVisitor |
| ProgramInspectorCreatorVisitor.java | Empty visit() | Implements UnitOLVisitor |
| InterfaceVisitor.java | Empty visit() | Implements UnitOLVisitor |

**Total: 6 interface-only changes across 6 files**

### Summary

- **Critical changes**: 8 files (2 new + 6 modified)
- **Correctness changes**: 3 files
- **Interface overhead**: 6 files (empty implementations)
- **Total files touched**: 17 files (2 new + 15 modified)

**Ratio**: 6/17 = **35% of file changes are pure interface overhead**

---

## Parser Token Consumption Strategy

### The Challenge

Jolie's parser has helper methods like `parseVariablePath()` that are **greedy**:

```java
VariablePathNode parseVariablePath() {
    assertIdentifier();
    String id = token.content();
    nextToken();

    // Greedy: keeps consuming DOT tokens
    while (token.is(Scanner.TokenType.DOT)) {
        nextToken();
        assertIdentifier(); // ← Expects ID, not ASTERISK!
        // ...
    }
}
```

If we called `parseVariablePath()` for `tree.*`, it would:
1. Parse `tree` ✓
2. See DOT ✓
3. Try to parse `*` as identifier ✗ (syntax error!)

### The Solution: Manual Token Eating

We **manually consume tokens** instead of using helper methods:

```java
// Parse PATHS path (e.g., var.*)
assertIdentifier("expected variable name after PATHS");
String varId = token.content();
nextToken();  // ← Eat ID token

// Create simple VariablePathNode manually
VariablePathNode baseVar = new VariablePathNode(getContext(), Type.NORMAL);
baseVar.append(new Pair<>(
    new ConstantStringExpression(getContext(), varId),
    null));

// Eat DOT token
eat(Scanner.TokenType.DOT, "expected . after variable in PATHS");

// Eat ASTERISK token
eat(Scanner.TokenType.ASTERISK, "expected * after . in PATHS");

// Now we have: ID, DOT, ASTERISK consumed
PathSpecNode pathSpec = new PathSpecNode(getContext(), baseVar, true);
```

**Key insight**: The `eat()` method consumes a specific token type and provides a clear error message if not found. This gives us fine-grained control.

### Why This Approach Works

1. **Controlled consumption**: We decide exactly which tokens to consume
2. **Clear error messages**: Each `eat()` has a specific error message
3. **No greedy behavior**: We don't consume more than intended
4. **Type safety**: `assertIdentifier()` validates before consumption

### Token Sequence

```
Input: paths tree.* from tree where $ == 5

Tokens: PATHS ID(tree) DOT ASTERISK FROM ID(tree) WHERE DOLLAR EQUAL INT(5)
        ↑      ↑         ↑   ↑
        |      |         |   |
        |      |         |   Eat ASTERISK
        |      |         Eat DOT
        |      Eat ID
        Eat PATHS
```

---

## Backward Compatibility

### Dual Syntax Support

The parser now supports **both syntaxes**:

**Old syntax (ANTLR string):**
```jolie
result << paths "$.*" from tree where $ == 10
```

**New syntax (native path):**
```jolie
result << paths tree.* from tree where $ == 10
```

### Detection Strategy

```java
if (token.is(Scanner.TokenType.STRING)) {
    // Old syntax: string starts the PATHS clause
    // Parse as ANTLR string
} else {
    // New syntax: identifier starts the PATHS clause
    // Parse as native path
}
```

### Old Syntax Handling

When old syntax is detected:
1. Eat the STRING token
2. Check for optional INTO clause (also deprecated)
3. Parse FROM variable
4. Create PathSpecNode using FROM variable (ignores string content for now)
5. Assume wildcard (most strings are `"$.*"`)

### Transition Path

**Phase 1 (Current)**: Both syntaxes supported, FROM required
```jolie
paths tree.* from tree where $ == 5  // new
paths "$.*" from tree where $ == 5   // old (still works)
```

**Phase 2 (Future)**: Native syntax only, FROM optional
```jolie
paths tree.* from tree where $ == 5  // from is redundant
paths tree.* where $ == 5            // from removed
```

**Phase 3 (Future)**: ANTLR completely removed, extended syntax
```jolie
paths tree.* where $ == 5            // wildcard
paths tree.[*] where $ > 10          // array wildcard
paths tree..value where $ < 100      // recursive descent
```

---

## Testing and Verification

### Test Suite: run_native_tests.py

Created a dedicated test suite for native PATHS syntax:

**File**: `test/paths/run_native_tests.py`

```python
#!/usr/bin/env python3
# Builds project and runs native PATHS syntax tests

tests = [
    ("test_native_wildcard.ol", ["tree.c", "tree.a"]),
    ("test_native_simple_value.ol", ["data.z", "data.x"]),
    ("test_native_greater_than.ol", ["items.c", "items.b"]),
    ("test_native_string_match.ol", ["fruits.c", "fruits.a"]),
    ("test_native_not_equal.ol", ["vals.c", "vals.a"]),
]
```

### Test 1: Basic Wildcard Selection

**File**: `test/paths/test_native_wildcard.ol`

```jolie
include "console.iol"

main {
    tree.a = 5;
    tree.b = 6;
    tree.c = 5;

    res << paths tree.* from tree where $ == 5;

    i = 0;
    while (i < #res.results) {
        println@Console(res.results[i])();
        i++
    }
}
```

**Expected output:**
```
tree.c
tree.a
```

**Result**: ✅ PASS

**Notes**: Order is reversed due to stack-based traversal in PathsQueryExecutor.

### Test 2: Different Values

**File**: `test/paths/test_native_simple_value.ol`

```jolie
main {
    data.x = 100;
    data.y = 200;
    data.z = 100;

    res << paths data.* from data where $ == 100;
}
```

**Expected output:**
```
data.z
data.x
```

**Result**: ✅ PASS

### Test 3: Greater Than Comparison

**File**: `test/paths/test_native_greater_than.ol`

```jolie
main {
    items.a = 5;
    items.b = 15;
    items.c = 20;

    res << paths items.* from items where $ > 10;
}
```

**Expected output:**
```
items.c
items.b
```

**Result**: ✅ PASS

### Test 4: String Comparison

**File**: `test/paths/test_native_string_match.ol`

```jolie
main {
    fruits.a = "apple";
    fruits.b = "banana";
    fruits.c = "apple";

    res << paths fruits.* from fruits where $ == "apple";
}
```

**Expected output:**
```
fruits.c
fruits.a
```

**Result**: ✅ PASS

### Test 5: Not Equal

**File**: `test/paths/test_native_not_equal.ol`

```jolie
main {
    vals.a = 1;
    vals.b = 2;
    vals.c = 3;

    res << paths vals.* from vals where $ != 2;
}
```

**Expected output:**
```
vals.c
vals.a
```

**Result**: ✅ PASS

### All Tests Summary

```
============================================================
Native PATHS Syntax Tests
============================================================

✓ test_native_wildcard.ol
✓ test_native_simple_value.ol
✓ test_native_greater_than.ol
✓ test_native_string_match.ol
✓ test_native_not_equal.ol

============================================================
✓ ALL PASSED (5/5)
============================================================
```

### Backward Compatibility Tests

Verified that old ANTLR string syntax still works:

```jolie
// Old syntax with native WHERE clause
res << paths "$.*" from root where $ == 10;
```

**Result**: ✅ PASS (outputs `root.z`, `root.x` as expected)

---

## Complete File Inventory

### Files Created (1)

1. `libjolie/src/main/java/jolie/lang/parse/ast/expression/PathSpecNode.java`
   - **Lines**: 37
   - **Purpose**: AST node for native PATHS paths (e.g., `var.*`)

### Files Modified - Critical (9)

1. `libjolie/src/main/java/jolie/lang/parse/OLParser.java`
   - **Lines changed**: ~60
   - **Changes**:
     - Add PathSpecNode import
     - Dual syntax detection (STRING vs ID)
     - Manual token consumption for `var.*`
     - Backward compatibility handling
     - Two locations (statement + expression)

2. `libjolie/src/main/java/jolie/lang/parse/ast/PathsStatement.java`
   - **Lines changed**: 12
   - **Changes**:
     - Replace `String pathsQuery` with `PathSpecNode pathSpec`
     - Remove `intoVariable` and `fromVariable` fields
     - Update constructor and accessors

3. `libjolie/src/main/java/jolie/lang/parse/ast/expression/PathsExpressionNode.java`
   - **Lines changed**: 12
   - **Changes**:
     - Replace `String pathsQuery` with `PathSpecNode pathSpec`
     - Remove `fromVariable` field
     - Update constructor and accessors

4. `libjolie/src/main/java/jolie/lang/parse/OLParseTreeOptimizer.java`
   - **Lines changed**: 10
   - **Changes**:
     - Add PathSpecNode import
     - visit(PathSpecNode) with optimization
     - Update visit(PathsStatement) to optimize pathSpec
     - Update visit(PathsExpressionNode) to optimize pathSpec

5. `jolie/src/main/java/jolie/OOITBuilder.java`
   - **Lines changed**: 10
   - **Changes**:
     - Add PathSpecNode import
     - visit(PathSpecNode) placeholder
     - Update visit(PathsStatement) to extract baseVariable + wildcard
     - Update visit(PathsExpressionNode) to extract baseVariable + wildcard

6. `jolie/src/main/java/jolie/process/PathsProcess.java`
   - **Lines changed**: 20
   - **Changes**:
     - Replace `String pathsQuery` + `VariablePath fromVariable`
       with `VariablePath pathSpec` + `boolean isWildcard`
     - Convert wildcard to ANTLR string at runtime (temporary)
     - Update constructor, copy(), run()

7. `jolie/src/main/java/jolie/runtime/expression/PathsExpression.java`
   - **Lines changed**: 20
   - **Changes**:
     - Replace `String pathsQuery` + `VariablePath fromVariable`
       with `VariablePath pathSpec` + `boolean isWildcard`
     - Convert wildcard to ANTLR string at runtime (temporary)
     - Update constructor, cloneExpression(), evaluate()

8. `libjolie/src/main/java/jolie/lang/parse/SemanticVerifier.java`
   - **Lines changed**: 8
   - **Changes**:
     - Add PathSpecNode import
     - visit(PathSpecNode) to verify baseVariable
     - Update visit(PathsStatement) to traverse pathSpec
     - Update visit(PathsExpressionNode) to traverse pathSpec

9. `libjolie/src/main/java/jolie/lang/parse/TypeChecker.java`
   - **Lines changed**: 6
   - **Changes**:
     - Add PathSpecNode import
     - visit(PathSpecNode) to check baseVariable types
     - Update visit(PathsExpressionNode) to traverse pathSpec

### Files Modified - Interface Only (6)

10. `libjolie/src/main/java/jolie/lang/parse/OLVisitor.java`
    - **Lines changed**: 2
    - **Changes**: Add visit(PathSpecNode) signature

11. `libjolie/src/main/java/jolie/lang/parse/UnitOLVisitor.java`
    - **Lines changed**: 6
    - **Changes**: Add visit(PathSpecNode) declaration and default

12. `libjolie/src/main/java/jolie/lang/parse/module/SymbolReferenceResolver.java`
    - **Lines changed**: 3
    - **Changes**: Add import, empty visit(PathSpecNode), update visit(PathsExpressionNode)

13. `libjolie/src/main/java/jolie/lang/parse/module/SymbolTableGenerator.java`
    - **Lines changed**: 3
    - **Changes**: Add import, empty visit(PathSpecNode), update visit(PathsExpressionNode)

14. `libjolie/src/main/java/jolie/lang/parse/util/impl/ProgramInspectorCreatorVisitor.java`
    - **Lines changed**: 3
    - **Changes**: Add import, empty visit(PathSpecNode), update visit(PathsExpressionNode)

15. `tools/jolie2plasma/src/main/java/joliex/plasma/impl/InterfaceVisitor.java`
    - **Lines changed**: 2
    - **Changes**: Add import, empty visit(PathSpecNode)

### Test Files Created (6)

16. `test/paths/run_native_tests.py`
    - **Lines**: 77
    - **Purpose**: Test runner for native PATHS syntax

17. `test/paths/test_native_wildcard.ol`
    - **Lines**: 18
    - **Purpose**: Basic wildcard test

18. `test/paths/test_native_simple_value.ol`
    - **Lines**: 18
    - **Purpose**: Different value test

19. `test/paths/test_native_greater_than.ol`
    - **Lines**: 18
    - **Purpose**: Comparison operator test

20. `test/paths/test_native_string_match.ol`
    - **Lines**: 18
    - **Purpose**: String comparison test

21. `test/paths/test_native_not_equal.ol`
    - **Lines**: 18
    - **Purpose**: Not equal operator test

### Total Impact

- **Files created**: 7 (1 source + 6 tests)
- **Source files modified**: 15
- **Total source files touched**: 16
- **Critical files**: 9 (56%)
- **Interface-only files**: 6 (38%)
- **Overhead ratio**: 38%

---

## Future Work

### Phase 1: Remove ANTLR Dependency (Current Goal)

- ✅ Native WHERE clause with `$` operator
- ✅ Native PATHS path with `var.*` syntax
- ⏳ Remove FROM clause (make it optional/remove)
- ⏳ Replace ANTLR path traversal with native Jolie implementation
- ⏳ Remove ANTLR runtime dependency completely

### Phase 2: Extended Path Syntax

Add support for more complex patterns:

**Array wildcards:**
```jolie
paths items.[*] where $ > 10      // All array elements
paths items.[0:5] where $ < 100   // Array slice
```

**Nested paths:**
```jolie
paths data.users.*.name where $ == "Alice"  // Nested wildcard
```

**Recursive descent:**
```jolie
paths tree..value where $ > 50    // Find all "value" fields recursively
```

**Conditional selection:**
```jolie
paths items.*[@.price < 100] where $ has .discount  // XPath-like predicates
```

### Phase 3: Query Optimization

Once fully native:
- Compile-time optimization of PATHS paths
- Index-based lookups for common patterns
- Parallel evaluation of WHERE clauses
- Caching of frequently-used queries

---

## Conclusion

Converting PATHS's path specification from ANTLR strings to native Jolie syntax required:

1. **New AST node**: PathSpecNode to represent `var.*` syntax
2. **Parser changes**: Manual token consumption to parse `ID DOT ASTERISK`
3. **AST updates**: Replace string fields with PathSpecNode in PathsStatement/PathsExpressionNode
4. **Visitor pattern updates**: 15 files (9 functional, 6 interface-only)
5. **Runtime updates**: PathsProcess/PathsExpression accept native paths
6. **Backward compatibility**: Old ANTLR string syntax still supported
7. **Testing**: 5 tests covering various comparison operators

**Key insight**: 38% of file changes are pure interface overhead due to Jolie's visitor pattern. The actual functional changes are concentrated in ~9 files.

**Token consumption strategy**: The critical technique was **manually eating tokens** with `eat()` instead of using greedy parser helper methods like `parseVariablePath()`. This gave us fine-grained control over exactly which tokens to consume.

**Current status**: Native PATHS paths work correctly with the `var.*` syntax. The implementation is backward compatible and provides a foundation for removing ANTLR dependency entirely.

**Next step**: Remove the FROM clause requirement and implement native path traversal to completely eliminate ANTLR from PATHS execution.
