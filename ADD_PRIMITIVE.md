# Adding Primitives to Jolie

This guide explains how to add primitive statements to the Jolie language, with or without arguments.

## Example: The `print` Primitive

We created a `print` primitive that evaluates an expression and outputs it to stdout.

Usage in Jolie code:
```jolie
main {
    a = 5;
    print a                    // Output: 5
    print "hello world"        // Output: hello world
    print 10 + 20             // Output: 30
}
```

## Files Modified/Created

### 1. Core Language Components (ESSENTIAL)

#### **Scanner.java** - Token Definition
Location: `libjolie/src/main/java/jolie/lang/parse/Scanner.java`

Add the token type to the `TokenType` enum:
```java
PRINT,  ///< print
```

Register the keyword mapping:
```java
UNRESERVED_KEYWORDS.put( "print", TokenType.PRINT );
```

#### **Keywords.java** - Keyword Registration
Location: `libjolie/src/main/java/jolie/lang/Keywords.java`

Add constant:
```java
public static final String PRINT = "print";
```

Add to MAIN_KEYWORDS list:
```java
private static final List< String > MAIN_KEYWORDS =
    List.of( "for", "while", "if", "else", "else if", "foreach", "with", "undef", "print",
        "synchronized", "scope", "install", "spawn", "over", "in", "throw", "cH", "comp", "nullProcess" );
```

#### **PrintStatement.java** - AST Node (NEW FILE)
Location: `libjolie/src/main/java/jolie/lang/parse/ast/PrintStatement.java`

```java
package jolie.lang.parse.ast;

import jolie.lang.parse.OLVisitor;
import jolie.lang.parse.context.ParsingContext;

public class PrintStatement extends OLSyntaxNode {
    private final OLSyntaxNode expression;

    public PrintStatement( ParsingContext context, OLSyntaxNode expression ) {
        super( context );
        this.expression = expression;
    }

    public OLSyntaxNode expression() {
        return expression;
    }

    @Override
    public < C, R > R accept( OLVisitor< C, R > visitor, C ctx ) {
        return visitor.visit( this, ctx );
    }
}
```

#### **OLParser.java** - Parser Logic
Location: `libjolie/src/main/java/jolie/lang/parse/OLParser.java`

Add import:
```java
import jolie.lang.parse.ast.PrintStatement;
```

Add parsing case in `parseBasicStatement()`:
```java
case PRINT:
    nextToken();
    retVal = new PrintStatement( getContext(), parseExpression() );
    break;
```

#### **OLVisitor.java** - Visitor Interface
Location: `libjolie/src/main/java/jolie/lang/parse/OLVisitor.java`

Add import:
```java
import jolie.lang.parse.ast.PrintStatement;
```

Add visitor method signature:
```java
R visit( PrintStatement n, C ctx );
```

#### **UnitOLVisitor.java** - Default Visitor Implementation
Location: `libjolie/src/main/java/jolie/lang/parse/UnitOLVisitor.java`

Add import:
```java
import jolie.lang.parse.ast.PrintStatement;
```

Add default implementation:
```java
void visit( PrintStatement n );

@Override
default Unit visit( PrintStatement n, Unit ctx ) {
    visit( n );
    return Unit.INSTANCE;
}
```

### 2. Runtime Components (ESSENTIAL)

#### **PrintProcess.java** - Runtime Process (NEW FILE)
Location: `jolie/src/main/java/jolie/process/PrintProcess.java`

```java
package jolie.process;

import jolie.ExecutionThread;
import jolie.runtime.Value;
import jolie.runtime.expression.Expression;

public class PrintProcess implements Process {
    private final Expression expression;

    public PrintProcess( Expression expression ) {
        this.expression = expression;
    }

    @Override
    public Process copy( TransformationReason reason ) {
        return new PrintProcess( expression.cloneExpression( reason ) );
    }

    @Override
    public void run() {
        if( ExecutionThread.currentThread().isKilled() )
            return;

        Value value = expression.evaluate();
        System.out.println( value.strValue() );
    }

    @Override
    public boolean isKillable() {
        return true;
    }
}
```

#### **OOITBuilder.java** - AST to Runtime Conversion
Location: `jolie/src/main/java/jolie/OOITBuilder.java`

Add imports:
```java
import jolie.lang.parse.ast.PrintStatement;
import jolie.process.PrintProcess;
```

Add visitor implementation:
```java
@Override
public void visit( PrintStatement n ) {
    currProcess = new PrintProcess( buildExpression( n.expression() ) );
}
```

### 3. Visitor Implementations (REQUIRED)

All classes implementing `UnitOLVisitor` must implement the `visit(PrintStatement)` method. Add the following to each:

**Import:**
```java
import jolie.lang.parse.ast.PrintStatement;
```

**Empty implementation (for most visitors):**
```java
@Override
public void visit( PrintStatement n ) {}
```

**Files to update:**
- `libjolie/src/main/java/jolie/lang/parse/TypeChecker.java`
- `libjolie/src/main/java/jolie/lang/parse/module/SymbolReferenceResolver.java`
- `libjolie/src/main/java/jolie/lang/parse/module/SymbolTableGenerator.java`
- `libjolie/src/main/java/jolie/lang/parse/util/impl/ProgramInspectorCreatorVisitor.java`
- `tools/jolie2plasma/src/main/java/joliex/plasma/impl/InterfaceVisitor.java`

**Special case - SemanticVerifier.java:**
Location: `libjolie/src/main/java/jolie/lang/parse/SemanticVerifier.java`

When your primitive has an expression parameter, you need to visit it:
```java
@Override
public void visit( PrintStatement n ) {
    n.expression().accept( this );
}
```

**Special case - OLParseTreeOptimizer.java:**
Location: `libjolie/src/main/java/jolie/lang/parse/OLParseTreeOptimizer.java`

When your primitive has an expression parameter, you need to optimize it:
```java
@Override
public void visit( PrintStatement n ) {
    currNode = new PrintStatement(
        n.context(),
        optimize( n.expression() ) );
}
```

## Build and Test

### Build the Project
```bash
mvn compile -DskipTests
```

### Test the New Primitive

Test with a variable:
```bash
echo 'main { a = 5; print a }' > /tmp/test_print.ol && \
java -cp "libjolie/target/classes:jolie/target/classes:jolie-cli/target/classes" \
     jolie.Jolie /tmp/test_print.ol && \
rm /tmp/test_print.ol
```

Expected output: `5`

Test with a string literal:
```bash
echo 'main { print "hello world" }' > /tmp/test_print.ol && \
java -cp "libjolie/target/classes:jolie/target/classes:jolie-cli/target/classes" \
     jolie.Jolie /tmp/test_print.ol && \
rm /tmp/test_print.ol
```

Expected output: `hello world`

Test with an expression:
```bash
echo 'main { x = 10; y = 20; print x + y }' > /tmp/test_print.ol && \
java -cp "libjolie/target/classes:jolie/target/classes:jolie-cli/target/classes" \
     jolie.Jolie /tmp/test_print.ol && \
rm /tmp/test_print.ol
```

Expected output: `30`

## Summary

To add a primitive statement to Jolie:

1. **Define the token** (Scanner.java, Keywords.java)
2. **Create AST node** (new PrintStatement.java)
   - For primitives with arguments: add fields and accessor methods
3. **Add parsing logic** (OLParser.java)
   - For primitives with arguments: call `parseExpression()` or similar
4. **Define visitor interface** (OLVisitor.java, UnitOLVisitor.java)
5. **Create runtime process** (new PrintProcess.java)
   - For primitives with arguments: accept Expression parameter and evaluate it
6. **Connect AST to runtime** (OOITBuilder.java)
   - For primitives with arguments: call `buildExpression()` to convert AST to runtime
7. **Implement visitor in all implementations**
   - Most are empty: SemanticVerifier (unless has expression), TypeChecker, Symbol resolvers
   - SemanticVerifier: if primitive has expression, visit it
   - OLParseTreeOptimizer: if primitive has expression, optimize it

The visitor pattern requires updating many files, but most are empty implementations satisfying the interface contract.

## File Count

- **New files**: PrintStatement.java, PrintProcess.java
- **Modified files**: Scanner, Keywords, Parser, Visitors, Builder
