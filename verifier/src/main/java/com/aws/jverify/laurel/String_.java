package com.aws.jverify.laurel;

public record String_(
    SourceRange sourceRange,
    java.lang.String s
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.string"; }
}
