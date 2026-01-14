package com.aws.jverify.laurel;

public record Return(
    SourceRange sourceRange,
    StmtExpr value
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.return"; }
}
