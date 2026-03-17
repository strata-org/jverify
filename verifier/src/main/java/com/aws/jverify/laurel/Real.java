package com.aws.jverify.laurel;

public record Real(
    SourceRange sourceRange,
    java.math.BigDecimal d
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.real"; }
}
