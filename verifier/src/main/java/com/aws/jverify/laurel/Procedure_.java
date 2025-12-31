package com.aws.jverify.laurel;

public record Procedure_(
    SourceRange sourceRange,
    java.lang.String name, StmtExpr body
) implements Procedure {
    @Override
    public java.lang.String operationName() { return "Laurel.procedure"; }
}
