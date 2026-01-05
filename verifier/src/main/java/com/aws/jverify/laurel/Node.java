package com.aws.jverify.laurel;

public sealed interface Node permits StmtExpr, Command, Procedure {
    SourceRange sourceRange();
    java.lang.String operationName();
}
