grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
SEMICOLLON : ';' ;
COLLON : ',' ;
DOT : '.' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
LSTRAIT : '[';
RSTRAIT : ']';
MUL : '*' ;
DIV : '/' ;
ADD : '+' ;
MINUS : '-' ;
AND : '&&' ;
OR : '||' ;
NOT : '!' ;
LESS : '<' ;
GREATER : '>' ;
LESSEQ : '<=' ;
GRATHEREQ : '>=' ;

CLASS : 'class' ;
IMPORT : 'import' ;
EXTENDS : 'extends' ;
INT : 'int' ;
INTSEQ : 'int...' ;
BOOLEAN : 'boolean' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;
LENGTH : 'length' ;
NEW : 'new' ;
TRUE: 'true';
FALSE: 'false';
THIS: 'this';
STATIC: 'static';

INTEGER : [0]|[1-9][0-9]* ;
ID : [a-zA-Z_$][a-zA-Z_0-9$]* ;

WS : [ \t\n\r\f]+ -> skip ;
MLC : '/*' .*? '*/' -> skip ;
SLC : '//' ~('\n'|'\r')* -> skip ;

program
    : (importDecl)* classDecl EOF
    ;

importDecl
    : IMPORT name+=ID ( DOT name+=ID )* SEMICOLLON
    ;

classDecl
    : CLASS name=ID (EXTENDS superClass=ID)? LCURLY varDecl* methodDecl* RCURLY
    ;

varDecl
    : type name=ID SEMICOLLON
    ;

type locals[boolean isArray=false]
    : name=INTSEQ
    | name=INT (LSTRAIT RSTRAIT {$isArray=true;})?
    | name=BOOLEAN
    | name=ID (LSTRAIT RSTRAIT {$isArray=true;})?
    ;

methodDecl locals[boolean isPublic=false, boolean isStatic=false]
    : (PUBLIC {$isPublic=true;})? (STATIC {$isStatic=true;})? type name=ID LPAREN (param (COLLON param)* )? RPAREN LCURLY varDecl* stmt* RCURLY
    ;

param
    : type name=ID
    ;

stmt
    : LCURLY stmt* RCURLY #BlockStmt
    | IF LPAREN expr RPAREN stmt (ELSE stmt)? #IfElseStmt
    | WHILE LPAREN expr RPAREN stmt #WhileStmt
    | expr SEMICOLLON #ExprStmt
    | expr EQUALS expr SEMICOLLON #AssignStmt
    | RETURN name=expr? SEMICOLLON #ReturnStmt
    ;

expr
    : LPAREN expr RPAREN #ParenExpr
    | NOT expr #UnaryOpExpr
    | expr LSTRAIT expr RSTRAIT #ArrayAccessExpr
    | LSTRAIT (expr (COLLON expr)* )? RSTRAIT #NewArrayExpr
    | expr DOT name=ID LPAREN (expr (COLLON expr)* )? RPAREN #MethodCallExpr
    | expr DOT LENGTH #LengthExpr
    | NEW INT LSTRAIT expr RSTRAIT #NewArraySizeExpr
    | NEW name=ID LPAREN RPAREN #NewClassExpr
    | expr op=(MUL|DIV) expr #BinaryExpr //
    | expr op=(ADD|MINUS) expr #BinaryExpr //
    | expr op=(LESS|GREATER|LESSEQ|GRATHEREQ) expr #BinaryExpr
    | expr op=(AND|OR) expr #BinaryExpr
    | THIS #ThisExpr
    | value=INTEGER #IntegerLiteral //
    | value=(TRUE|FALSE) #BooleanLiteral
    | name=ID #VarRefExpr //
    ;



