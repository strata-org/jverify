package com.aws.jverify.laurel;

public record Int(
    SourceRange sourceRange,
    java.math.BigInteger n
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.int"; }
}
