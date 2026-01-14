package com.aws.jverify.laurel;

public record Assign(
    SourceRange sourceRange,
    StmtExpr target, StmtExpr value
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.assign"; }
}
