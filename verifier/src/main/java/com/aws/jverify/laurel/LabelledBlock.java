package com.aws.jverify.laurel;

public record LabelledBlock(
    SourceRange sourceRange,
    java.util.List<StmtExpr> stmts, java.lang.String label
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.labelledBlock"; }
}
