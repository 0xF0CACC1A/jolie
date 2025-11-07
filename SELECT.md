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
java -cp "libjolie/target/classes:jolie/target/classes:jolie-cli/target/classes:libjolie/target/generated-sources/antlr4:~/.m2/repository/org/antlr/antlr4-runtime/4.13.1/antlr4-runtime-4.13.1.jar" jolie.Jolie program.ol
```

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

## Impact Summary

**Compilation**: Requires ANTLR Maven plugin to generate parser classes before Java compilation

**Runtime**: Requires ANTLR runtime JAR on classpath for query parsing

**AST Layer**: Introduces string-based AST fields (break from expression-node pattern)

**Visitor Pattern**: All visitor implementations must add `visit(SelectStatement)` method

**Semantic Analysis**: Query strings bypass normal expression validation

**Type System**: No compile-time type checking for query syntax (validated at runtime)
