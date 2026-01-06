package com.aws.jverify.laurel;

public record Block(
    SourceRange sourceRange,
    java.util.List<StmtExpr> stmts
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.block"; }
}
