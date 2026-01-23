package com.aws.jverify.laurel;

public record ExistsExpr(
    SourceRange sourceRange,
    java.lang.String name, LaurelType ty, StmtExpr body
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.existsExpr"; }
}
