# Adding the SELECT Primitive to Jolie

This document details the implementation of the SELECT primitive statement in Jolie. For general guidance on adding primitives, see [ADD_PRIMITIVE.md](ADD_PRIMITIVE.md).

## Overview

SELECT enables JSONPath-like queries on Jolie value trees with WHERE clause filtering. SELECT uses ANTLR4 for runtime query parsing, requiring string-based AST fields instead of expression nodes.

### Syntax

```jolie
SELECT <query-string> INTO <variable> FROM <variable> WHERE <condition-string>
```

Results are stored as an array: `results[0]`, `results[1]`, etc.

## Architecture Differences

**Key distinction from expression-based primitives**:
- SELECT and WHERE clauses are stored as **strings** in the AST, not expression nodes
- Strings are parsed at **runtime** using ANTLR-generated parsers
- Requires ANTLR4 Maven dependencies and grammar file

## Files Changed

### New Files Created

1. **`libjolie/src/main/antlr4/jolie/lang/parse/select/SelectQuery.g4`**
   - ANTLR grammar defining SELECT and WHERE query syntax
   - Automatically generates lexer/parser classes during Maven build

2. **`libjolie/src/main/java/jolie/lang/parse/ast/SelectStatement.java`**
   - AST node with 4 fields: `String selectQuery`, `VariablePathNode intoVariable`, `VariablePathNode fromVariable`, `String whereQuery`
   - **Critical**: Query fields are `String`, not `OLSyntaxNode`

3. **`jolie/src/main/java/jolie/process/SelectProcess.java`**
   - Runtime process with 4 fields matching AST (queries as `String`, variables as `VariablePath`)
   - Calls `SelectQueryExecutor.execute()` for query processing
   - Stores results using `intoVariable.getValueVector().get(i).setValue()`
   - Contains `extractRootPath()` helper to build full variable path string

4. **`jolie/src/main/java/jolie/runtime/select/SelectQueryExecutor.java`**
   - Static executor using ANTLR-generated parsers
   - Stack-based iterative tree traversal
   - Nested `WhereEvaluator` class for boolean expression evaluation
   - Returns `List<String>` of matching path strings

### Modified Files

#### Maven Configuration

**`libjolie/pom.xml`**
- Added ANTLR4 runtime dependency (4.13.1)
- Added ANTLR4 Maven plugin for code generation
- Plugin configured to read from `src/main/antlr4` and output to `target/generated-sources/antlr4`

#### Lexer/Parser Layer

**`libjolie/src/main/java/jolie/lang/parse/Scanner.java`**
- Added 3 token types: `SELECT`, `INTO`, `WHERE`
- Registered keyword mappings in `UNRESERVED_KEYWORDS`

**`libjolie/src/main/java/jolie/lang/Keywords.java`**
- Added 3 constants: `SELECT`, `INTO`, `WHERE`
- Added to `MAIN_KEYWORDS` list

**`libjolie/src/main/java/jolie/lang/parse/OLParser.java`**
- Added import for `SelectStatement`
- Added parsing case in `parseBasicStatement()`:
  - Uses `assertToken(STRING)` to enforce string literals for queries
  - Extracts string content with `.replaceAll("\"", "")`
  - Uses `eat()` to enforce keyword sequence: SELECT → INTO → FROM → WHERE
  - Calls `parseVariablePath()` for INTO/FROM variables
  - Creates `SelectStatement` with strings (not expression nodes)

#### Visitor Pattern

**`libjolie/src/main/java/jolie/lang/parse/OLVisitor.java`**
- Added import for `SelectStatement`
- Added method signature: `R visit( SelectStatement n, C ctx )`

**`libjolie/src/main/java/jolie/lang/parse/UnitOLVisitor.java`**
- Added import for `SelectStatement`
- Added default implementation delegating to single-parameter `visit(SelectStatement)`

#### AST to Runtime Conversion

**`jolie/src/main/java/jolie/OOITBuilder.java`**
- Added imports for `SelectStatement` and `SelectProcess`
- Added visitor implementation:
  ```java
  @Override
  public void visit( SelectStatement n ) {
      currProcess = new SelectProcess(
          n.selectQuery(),                    // String, not buildExpression()!
          buildVariablePath( n.intoVariable() ),
          buildVariablePath( n.fromVariable() ),
          n.whereQuery() );                   // String, not buildExpression()!
  }
  ```
- **Critical mistake to avoid**: Using `buildExpression()` on query strings causes errors

#### Semantic Analysis

**`libjolie/src/main/java/jolie/lang/parse/SemanticVerifier.java`**
- Added import for `SelectStatement`
- Visits only variable paths (not query strings):
  ```java
  @Override
  public void visit( SelectStatement n ) {
      n.intoVariable().accept( this );
      n.fromVariable().accept( this );
  }
  ```
- **Why different**: Query strings have no AST nodes to validate

**`libjolie/src/main/java/jolie/lang/parse/OLParseTreeOptimizer.java`**
- Added import for `SelectStatement`
- Pass through query strings unchanged, optimize variable paths:
  ```java
  @Override
  public void visit( SelectStatement n ) {
      currNode = new SelectStatement(
          n.context(),
          n.selectQuery(),                    // Pass through string
          optimizePath( n.intoVariable() ),
          optimizePath( n.fromVariable() ),
          n.whereQuery() );                   // Pass through string
  }
  ```

#### Empty Visitor Stubs

Added empty `visit(SelectStatement n) {}` implementations to:
- `libjolie/src/main/java/jolie/lang/parse/TypeChecker.java`
- `libjolie/src/main/java/jolie/lang/parse/module/SymbolReferenceResolver.java`
- `libjolie/src/main/java/jolie/lang/parse/module/SymbolTableGenerator.java`
- `libjolie/src/main/java/jolie/lang/parse/util/impl/ProgramInspectorCreatorVisitor.java`
- `tools/jolie2plasma/src/main/java/joliex/plasma/impl/InterfaceVisitor.java`

## Build Configuration

### Maven Build

```bash
mvn compile -DskipTests
```

ANTLR plugin runs during compilation, generating parser classes in `libjolie/target/generated-sources/antlr4`.

### Runtime Classpath

When executing Jolie programs with SELECT, ANTLR runtime JAR must be on classpath:

```bash
java -cp "libjolie/target/classes:jolie/target/classes:jolie-cli/target/classes:libjolie/target/generated-sources/antlr4:test/select/antlr4-runtime-4.13.1.jar" jolie.Jolie program.ol
```

**Note**: The ANTLR runtime JAR is included in `test/select/antlr4-runtime-4.13.1.jar` for convenience.

## Key Architectural Decisions

1. **Runtime parsing**: Query strings parsed at runtime (not compile-time) using ANTLR
2. **String-based AST**: `SelectStatement` stores strings, not `OLSyntaxNode` expression trees
3. **No expression building**: OOITBuilder passes strings directly, doesn't call `buildExpression()`
4. **No semantic validation**: SemanticVerifier skips query strings, only visits variable paths
5. **Direct array storage**: Results stored as `results[0]`, `results[1]` using `getValueVector()`
6. **Static executor**: SelectQueryExecutor provides static `execute()` method, no instantiation

## File Change Necessity Analysis

### Strictly Required (Core Implementation)

Cannot compile or execute without these files:

1. **libjolie/pom.xml** - ANTLR4 runtime dependency + Maven plugin
2. **libjolie/src/main/antlr4/jolie/lang/parse/select/SelectQuery.g4** - Grammar for query syntax
3. **libjolie/src/main/java/jolie/lang/parse/Scanner.java** - SELECT, INTO, WHERE tokens
4. **libjolie/src/main/java/jolie/lang/Keywords.java** - Keyword registration
5. **libjolie/src/main/java/jolie/lang/parse/OLParser.java** - Parsing logic with string extraction
6. **libjolie/src/main/java/jolie/lang/parse/ast/SelectStatement.java** - AST node (string-based fields)
7. **libjolie/src/main/java/jolie/lang/parse/OLVisitor.java** - Visitor interface signature
8. **libjolie/src/main/java/jolie/lang/parse/UnitOLVisitor.java** - Default visitor implementation
9. **jolie/src/main/java/jolie/process/SelectProcess.java** - Runtime execution process
10. **jolie/src/main/java/jolie/runtime/select/SelectQueryExecutor.java** - ANTLR query executor

### Files With Actual Implementation Logic

These files contain non-trivial logic for SELECT:

1. **jolie/src/main/java/jolie/OOITBuilder.java** - Creates SelectProcess, passes query strings
2. **libjolie/src/main/java/jolie/lang/parse/SemanticVerifier.java** - Validates variable paths (skips queries)
3. **libjolie/src/main/java/jolie/lang/parse/OLParseTreeOptimizer.java** - Optimizes variable paths (passes through queries)

### Empty Visitor Stubs (Required for Compilation)

These files contain only empty `visit(SelectStatement n) {}` stubs.
Required because all classes implementing `UnitOLVisitor` must provide the method:

1. **libjolie/src/main/java/jolie/lang/parse/TypeChecker.java**
2. **libjolie/src/main/java/jolie/lang/parse/module/SymbolReferenceResolver.java**
3. **libjolie/src/main/java/jolie/lang/parse/module/SymbolTableGenerator.java**
4. **libjolie/src/main/java/jolie/lang/parse/util/impl/ProgramInspectorCreatorVisitor.java**
5. **tools/jolie2plasma/src/main/java/joliex/plasma/impl/InterfaceVisitor.java**

## Common Implementation Pitfalls

1. **Using `buildExpression()` in OOITBuilder**: Query fields are strings, not expression nodes
2. **Visiting query expressions in SemanticVerifier**: No AST nodes to visit for string queries
3. **Forgetting string extraction in parser**: Must use `.replaceAll("\"", "")` to extract content
4. **Incomplete path extraction**: `extractRootPath()` must traverse all path segments, not just first
5. **Missing ANTLR runtime at execution**: JAR must be on classpath when running Jolie programs
6. **Wrong result storage**: Use `getValueVector()` for array storage, not nested fields

## Converting SELECT to an Expression Primitive

The SELECT primitive was originally implemented as a statement but can also be used as an expression with the deep copy operator `<<`. This section documents the conversion process.

### Expression vs Statement Usage

**Statement syntax (original):**
```jolie
select "$.*" into results from root where ". == 10"
```

**Expression syntax (with deep copy):**
```jolie
result << select "$.*" into results from root where ". == 10"
```

Both syntaxes work simultaneously. The INTO keyword remains functional in both cases.

### Files Required for Expression Support

#### 1. Core Expression Implementation (Strictly Required)

**`libjolie/src/main/java/jolie/lang/parse/ast/expression/SelectExpressionNode.java`**
- New AST node in `expression` package (not `ast` package like SelectStatement)
- Identical structure to SelectStatement: 4 fields (2 strings, 2 VariablePathNode)
- Must implement `accept()` method calling `visitor.visit(this, ctx)`

**`jolie/src/main/java/jolie/runtime/expression/SelectExpression.java`**
- Implements `Expression` interface with two methods:
  - `Value evaluate()` - executes SELECT query and returns result Value
  - `Expression cloneExpression(TransformationReason)` - clones for spawn/parallel
- Contains same logic as SelectProcess.run() but returns a Value
- Returns Value with structure: `result.getChildren("result").get(i)` containing matching paths

**`libjolie/src/main/java/jolie/lang/parse/OLVisitor.java`**
- Add interface method: `R visit(SelectExpressionNode n, C ctx);`
- Placed with other expression visitor methods (near IfExpressionNode)

**`libjolie/src/main/java/jolie/lang/parse/OLParser.java`**
- Add `case SELECT:` block in `parseFactor()` method (NOT parseBasicStatement)
- Identical parsing logic to statement version
- Creates `SelectExpressionNode` instead of `SelectStatement`
- Add import: `import jolie.lang.parse.ast.expression.SelectExpressionNode;`

**`jolie/src/main/java/jolie/OOITBuilder.java`**
- Add `visit(SelectExpressionNode n)` method
- Sets `currExpression` (not `currProcess`)
- Creates `SelectExpression` with same parameters as SelectProcess
- Add imports for both SelectExpressionNode and SelectExpression

#### 2. Visitor Interface Implementation (Required for Compilation)

**`libjolie/src/main/java/jolie/lang/parse/UnitOLVisitor.java`**
- Add method declaration: `void visit(SelectExpressionNode n);`
- Add default implementation delegating to single-parameter visit

All classes implementing UnitOLVisitor must add visit method:

**Files with Logic (actual implementation):**
- `libjolie/src/main/java/jolie/lang/parse/SemanticVerifier.java`
  - Visits variable paths: `n.intoVariable().accept(this); n.fromVariable().accept(this);`
  - Add import: `import jolie.lang.parse.ast.expression.SelectExpressionNode;`

- `libjolie/src/main/java/jolie/lang/parse/TypeChecker.java`
  - Same as SemanticVerifier

- `libjolie/src/main/java/jolie/lang/parse/OLParseTreeOptimizer.java`
  - Optimizes variable paths, reconstructs SelectExpressionNode
  - Pattern: `currNode = new SelectExpressionNode(n.context(), n.selectQuery(), optimizeNode(n.intoVariable()), optimizeNode(n.fromVariable()), n.whereQuery());`
  - Add import: `import jolie.lang.parse.ast.expression.SelectExpressionNode;`

- `libjolie/src/main/java/jolie/lang/parse/module/SymbolReferenceResolver.java`
  - Visits variable paths
  - Add import: `import jolie.lang.parse.ast.expression.SelectExpressionNode;`

- `libjolie/src/main/java/jolie/lang/parse/module/SymbolTableGenerator.java`
  - Visits variable paths
  - Add import: `import jolie.lang.parse.ast.expression.SelectExpressionNode;`

**Files with Empty Stubs (satisfy interface only):**
- `libjolie/src/main/java/jolie/lang/parse/util/impl/ProgramInspectorCreatorVisitor.java`
  - Empty implementation: `public void visit(SelectExpressionNode n) { n.intoVariable().accept(this); n.fromVariable().accept(this); }`

- `tools/jolie2plasma/src/main/java/joliex/plasma/impl/InterfaceVisitor.java`
  - Empty stub: `public void visit(SelectExpressionNode n) {}`

### Key Architectural Decisions for Expressions

1. **parseFactor() not parseBasicStatement()**: Expressions are parsed in factor parsing, statements in statement parsing
2. **currExpression not currProcess**: OOITBuilder sets currExpression field for expressions
3. **Return Value from evaluate()**: Expression.evaluate() must return a Value, not void
4. **Deep copy semantics**: The `<<` operator calls evaluate() and deep-copies result
5. **Side effects preserved**: Even though it's an expression, INTO still modifies intoVariable
6. **Dual return**: evaluate() both modifies intoVariable AND returns a result Value

### Strictly Required vs Interface Satisfaction

**Strictly Required (7 files):**
1. SelectExpressionNode.java (AST)
2. SelectExpression.java (runtime)
3. OLVisitor.java (interface signature)
4. OLParser.java (parsing logic)
5. OOITBuilder.java (builds runtime from AST)
6. UnitOLVisitor.java (default visitor)
7. One of: SemanticVerifier/TypeChecker (to implement the interface method)

**Interface Satisfaction Only (6 files):**
- TypeChecker, OLParseTreeOptimizer, SymbolReferenceResolver, SymbolTableGenerator, ProgramInspectorCreatorVisitor, InterfaceVisitor
- Contain either empty implementations or simple variable path visitation
- Required only because UnitOLVisitor is an interface, not abstract class
- Java requires all interface methods to be implemented by concrete classes

### Common Pitfalls for Expression Conversion

1. **Parsing in wrong location**: Must add to parseFactor(), not parseBasicStatement()
2. **Missing imports**: Each visitor file needs SelectExpressionNode import
3. **Wrong builder field**: Must set currExpression, not currProcess
4. **Forgetting return value**: evaluate() must return a Value, cannot be void
5. **Missing import in OOITBuilder**: Need both AST and runtime SelectExpression imports
6. **Incomplete visitor updates**: All 7 UnitOLVisitor implementations must be updated

## Impact Summary

**Compilation**: Requires ANTLR Maven plugin to generate parser classes before Java compilation

**Runtime**: Requires ANTLR runtime JAR on classpath for query parsing

**AST Layer**: Introduces string-based AST fields (break from expression-node pattern)

**Visitor Pattern**: All visitor implementations must add `visit(SelectStatement)` method

**Semantic Analysis**: Query strings bypass normal expression validation

**Type System**: No compile-time type checking for query syntax (validated at runtime)

## Test Coverage

Tests located in `test/select/` verify the following features:

**SELECT clause navigation:**
- `$.*` - wildcard navigation
- `$[*]` - array iteration
- `$..field` - descendant search
- `$.field`, `$[*].field` - direct navigation
- `$[*]..projects[*].status` - composed descendant with field navigation

**WHERE clause conditions:**
- `.field == value` - direct field value check
- `.a.b.c == value` - nested path value check
- `..field[*] == value` - descendant array search
- `.field in .` - field existence check
- `.a.b.c in .` - nested path existence check

**Boolean operators:**
- `&&` - AND operator
- `||` - OR operator
- `!` - NOT operator
- `()` - parentheses for precedence

**Expression usage:**
- `test_expression_equality.ol` - Verifies SELECT as expression with deep copy operator
- Compares `results1` (INTO side effect) with `result2.result` (expression return value)
- Validates that both the side effect and return value contain identical results

**Run tests:**
```bash
cd test/select && python3 run_tests.py
```
