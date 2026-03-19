package com.aws.jverify.laurel;

public record New(
    SourceRange sourceRange,
    java.lang.String name
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.new"; }
}
