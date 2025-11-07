/*
 * ANTLR4 grammar for Jolie SELECT queries
 * Supports JSONPath-like navigation with WHERE filtering
 */

grammar SelectQuery;

// Parser rules
selectClause
    : '$' '[*]' segment* EOF    # SelectWithArray
    | '$' segment* EOF          # SelectTree
    ;

segment
    : '..' ID '[*]'             # DescendantArraySegment
    | '..' ID                   # DescendantSegment
    | '.' token                 # DotSegment
    ;

token
    : '*[*]'                    # WildcardArray
    | '*'                       # Wildcard
    | ID '[*]'                  # FieldArray
    | ID                        # Field
    ;

// WHERE clause with parentheses support
whereClause
    : orExpr
    ;

orExpr
    : andExpr ('||' andExpr)*
    ;

andExpr
    : notExpr ('&&' notExpr)*
    ;

notExpr
    : '!' notExpr               # NotExpression
    | primary                   # PrimaryExpression
    ;

primary
    : '(' orExpr ')'            # ParenExpression
    | condition                 # ConditionExpression
    ;

condition
    : '.' '==' value            # NodeValue
    | wherePath 'in' '.'        # PathExists
    | wherePath '==' value      # PathMatch
    ;

wherePath
    : wherePathSegment+
    ;

wherePathSegment
    : '..' ID '[*]'             # WhereDescendantArraySegment
    | '..' ID                   # WhereDescendantSegment
    | '.' ID                    # WhereDirectSegment
    ;

value
    : ID | NUMBER
    ;

// Lexer rules
ID      : [a-zA-Z_][a-zA-Z0-9_]* ;
NUMBER  : [0-9]+ ;
WS      : [ \t\r\n]+ -> skip ;
