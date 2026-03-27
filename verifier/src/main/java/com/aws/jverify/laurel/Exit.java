package com.aws.jverify.laurel;

public record Exit(
    SourceRange sourceRange,
    java.lang.String label
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.exit"; }
}
