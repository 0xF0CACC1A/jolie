# Adding a Simple Primitive to Jolie

This guide explains how to add a simple primitive statement to the Jolie language that takes **no arguments**, similar to `nullProcess`.

## Example: Adding the `print` Primitive

We created a `print` primitive that outputs "hello" when executed.

Usage in Jolie code:
```jolie
main {
    print
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
    public PrintStatement( ParsingContext context ) {
        super( context );
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
    retVal = new PrintStatement( getContext() );
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

public class PrintProcess implements Process {
    public PrintProcess() {}

    @Override
    public Process copy( TransformationReason reason ) {
        return new PrintProcess();
    }

    @Override
    public void run() {
        if( ExecutionThread.currentThread().isKilled() )
            return;

        // Mockup: print "hello" when PRINT is encountered
        System.out.println( "hello" );
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
    currProcess = new PrintProcess();
}
```

### 3. Visitor Implementations (REQUIRED)

All classes implementing `UnitOLVisitor` must implement the `visit(PrintStatement)` method. Add the following to each:

**Import:**
```java
import jolie.lang.parse.ast.PrintStatement;
```

**Empty implementation:**
```java
@Override
public void visit( PrintStatement n ) {}
```

**Files to update:**
- `libjolie/src/main/java/jolie/lang/parse/SemanticVerifier.java`
- `libjolie/src/main/java/jolie/lang/parse/TypeChecker.java`
- `libjolie/src/main/java/jolie/lang/parse/module/SymbolReferenceResolver.java`
- `libjolie/src/main/java/jolie/lang/parse/module/SymbolTableGenerator.java`
- `libjolie/src/main/java/jolie/lang/parse/util/impl/ProgramInspectorCreatorVisitor.java`
- `tools/jolie2plasma/src/main/java/joliex/plasma/impl/InterfaceVisitor.java`

**Special case - OLParseTreeOptimizer.java:**
Location: `libjolie/src/main/java/jolie/lang/parse/OLParseTreeOptimizer.java`

```java
@Override
public void visit( PrintStatement n ) {
    currNode = n;  // Pass through without optimization
}
```

## Build and Test

### Build the Project
```bash
mvn compile -DskipTests
```

### Test the New Primitive

Run with compiled classes directly (creates temporary test file):
```bash
echo 'main { print }' > /tmp/test_print.ol && \
java -cp "libjolie/target/classes:jolie/target/classes:jolie-cli/target/classes" \
     jolie.Jolie /tmp/test_print.ol && \
rm /tmp/test_print.ol
```

Expected output:
```
hello
```

## Summary

To add a simple no-argument primitive to Jolie:

1. **Define the token** (Scanner.java, Keywords.java)
2. **Create AST node** (new PrintStatement.java)
3. **Add parsing logic** (OLParser.java)
4. **Define visitor interface** (OLVisitor.java, UnitOLVisitor.java)
5. **Create runtime process** (new PrintProcess.java)
6. **Connect AST to runtime** (OOITBuilder.java)
7. **Implement visitor in all implementations** (SemanticVerifier, TypeChecker, etc.)

The visitor pattern requires updating many files, but most are empty implementations satisfying the interface contract.

## File Count

- **New files**: PrintStatement.java, PrintProcess.java
- **Modified files**: Scanner, Keywords, Parser, Visitors, Builder
