package com.aws.jverify.laurel;

public record NondetHole(
    SourceRange sourceRange
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.nondetHole"; }
}
