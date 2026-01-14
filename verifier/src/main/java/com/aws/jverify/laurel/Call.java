package com.aws.jverify.laurel;

public record Call(
    SourceRange sourceRange,
    StmtExpr callee, java.util.List<StmtExpr> args
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.call"; }
}
