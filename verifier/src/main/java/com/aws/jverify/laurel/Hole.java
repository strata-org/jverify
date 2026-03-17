package com.aws.jverify.laurel;

public record Hole(
    SourceRange sourceRange
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.hole"; }
}
