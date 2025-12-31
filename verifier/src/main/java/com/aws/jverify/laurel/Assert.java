package com.aws.jverify.laurel;

public record Assert(
    SourceRange sourceRange,
    StmtExpr cond
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.assert"; }
}
