package com.aws.jverify.laurel;

public record Parenthesis(
    SourceRange sourceRange,
    StmtExpr inner
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.parenthesis"; }
}
