package com.aws.jverify.laurel;

public record ArrayIndex(
    SourceRange sourceRange,
    StmtExpr arr, StmtExpr idx
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.arrayIndex"; }
}
