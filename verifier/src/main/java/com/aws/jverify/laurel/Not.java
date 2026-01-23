package com.aws.jverify.laurel;

public record Not(
    SourceRange sourceRange,
    StmtExpr inner
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.not"; }
}
