# Removal of INTO Clause from PATHS Primitive

## Summary

This document describes the changes made to remove the `INTO` clause from Jolie's PATHS primitive, replacing it with the `<<` operator for result assignment.

## Motivation

The PATHS primitive originally used an explicit `INTO` clause to specify where results should be stored:
```jolie
paths "$.*" into results from data where $ == 5
```

This syntax was redundant since Jolie already has the `<<` operator for assignment. The new syntax is more consistent with the rest of the language:
```jolie
results << paths "$.*" from data where $ == 5
```

## Syntax Changes

### Before
```jolie
paths "query" into results from data where expression
```

### After
```jolie
results << paths "query" from data where expression
```

The `INTO variable` clause has been completely removed. PATHS can now be used as:
1. **Expression form** (with `<<`): Returns results that can be assigned to a variable
2. **Statement form** (standalone): Evaluates but doesn't store results (effectively a no-op)

## Implementation Changes

### 1. Parser Changes (OLParser.java)

**PathsStatement parsing** (lines 2511-2520):
- Removed: `eat(Scanner.TokenType.INTO, ...)`
- Removed: `VariablePathNode intoVar = parseVariablePath()`
- Changed constructor call to exclude `intoVar` parameter

**PathsExpressionNode parsing** (lines 3686-3695):
- Same changes as PathsStatement

### 2. AST Changes

**PathsStatement.java**:
- Removed field: `private final VariablePathNode intoVariable`
- Removed accessor: `public VariablePathNode intoVariable()`
- Updated constructor to accept only: `pathsQuery`, `fromVariable`, `whereExpression`

**PathsExpressionNode.java**:
- Identical changes to PathsStatement

### 3. Runtime Changes

**PathsProcess.java**:
- Removed field: `private final VariablePath intoVariable`
- Removed parameter from constructor
- Modified `run()` method:
  - Removed result storage logic
  - Added comment explaining statement form is now no-op
  - PATHS as statement computes results but doesn't store them

**PathsExpression.java**:
- Removed field: `private final VariablePath intoVariable`
- Removed parameter from constructor
- Modified `evaluate()` method:
  - No longer stores results in INTO variable
  - Returns results as `Value` array under `results` field
  - Format: `result.results[0]`, `result.results[1]`, etc.

### 4. Visitor Pattern Updates

All visitor implementations required updates to handle the removed parameter:

**Critical visitors** (contain actual logic):
- `OLParseTreeOptimizer.java` (line 800-806): Updated to pass 2 parameters instead of 3
- `OOITBuilder.java` (lines 1499-1503, 1735-1740): Updated constructor calls
- `SemanticVerifier.java` (lines 979-982, 1225-1228): Removed `intoVariable` visit

**Interface-only visitors** (removed intoVariable visit call):
- `TypeChecker.java` (line 671-673)
- `SymbolReferenceResolver.java` (line ~521)
- `SymbolTableGenerator.java` (line ~299)
- `ProgramInspectorCreatorVisitor.java` (line ~454)

**Interface files**:
- `OLVisitor.java`: No changes needed (interface methods don't specify parameter names)
- `UnitOLVisitor.java`: No changes needed

### 5. Cleanup: Removed HasExpressionNode

During this refactoring, we also removed all references to `HasExpressionNode`, which was part of previous incomplete work:
- Removed from `OLVisitor.java`
- Removed from `UnitOLVisitor.java`
- Removed from all visitor implementations (8 files)
- Removed import statements and empty visitor methods

## Files Modified

### AST and Parser (5 files)
1. `libjolie/src/main/java/jolie/lang/parse/OLParser.java`
2. `libjolie/src/main/java/jolie/lang/parse/ast/PathsStatement.java`
3. `libjolie/src/main/java/jolie/lang/parse/ast/expression/PathsExpressionNode.java`
4. `libjolie/src/main/java/jolie/lang/parse/OLParseTreeOptimizer.java`
5. `libjolie/src/main/java/jolie/lang/parse/SemanticVerifier.java`

### Runtime (3 files)
6. `jolie/src/main/java/jolie/OOITBuilder.java`
7. `jolie/src/main/java/jolie/process/PathsProcess.java`
8. `jolie/src/main/java/jolie/runtime/expression/PathsExpression.java`

### Visitor Interfaces and Implementations (7 files)
9. `libjolie/src/main/java/jolie/lang/parse/OLVisitor.java`
10. `libjolie/src/main/java/jolie/lang/parse/UnitOLVisitor.java`
11. `libjolie/src/main/java/jolie/lang/parse/TypeChecker.java`
12. `libjolie/src/main/java/jolie/lang/parse/module/SymbolReferenceResolver.java`
13. `libjolie/src/main/java/jolie/lang/parse/module/SymbolTableGenerator.java`
14. `libjolie/src/main/java/jolie/lang/parse/util/impl/ProgramInspectorCreatorVisitor.java`
15. `tools/jolie2plasma/src/main/java/joliex/plasma/impl/InterfaceVisitor.java`

**Total: 15 files modified**

## Testing

### Test Case
```jolie
include "console.iol"

main {
    root.x = 5;
    root.y = 10;
    root.z = 5;

    // New syntax with << operator
    results << paths "$.*" from root where $ == 5;

    println@Console("Results with $ == 5:")();
    i = 0;
    while( i < #results.results ) {
        println@Console("  " + results.results[i])();
        i++
    }
}
```

### Expected Output
```
Results with $ == 5:
  root.z
  root.x
```

### Test Result
✅ **PASSED** - Correctly filters and returns values equal to 5

## Build Status

- **Compilation**: ✅ Success
- **Tests**: ✅ Manual test passed
- **PMD violations**: 6 (all in ANTLR-generated code, not our changes)

## Breaking Changes

This is a **breaking change** for existing Jolie code using PATHS:

### Migration Guide

**Old code:**
```jolie
paths "$.items[*]" into results from data where $ == 5
```

**New code:**
```jolie
results << paths "$.items[*]" from data where $ == 5
```

**Steps to migrate:**
1. Remove `into variableName` from PATHS statement
2. Add `variableName <<` before PATHS keyword
3. Results are now stored in `variableName.results[i]` instead of `variableName[i]`

## Related Work

This change builds upon:
- **Previous commit**: "Implement native Jolie WHERE clause for PATHS primitive"
  - Converted WHERE from ANTLR string to native Jolie expressions
  - Added `$` operator for current value reference

Combined, these changes make PATHS fully integrated with Jolie's native syntax rather than using external DSL constructs.

## Future Considerations

1. **Result format**: Currently returns `result.results[0]`, `result.results[1]`. Could be simplified to return a direct array.
2. **Statement form**: PATHS as a standalone statement is now a no-op. Consider removing support or adding a warning.
3. **Documentation**: All PATHS documentation and examples need updating.
4. **Migration tool**: Could create a script to automatically convert old PATHS syntax to new syntax.
