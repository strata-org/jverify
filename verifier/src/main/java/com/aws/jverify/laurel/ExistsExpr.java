package com.aws.jverify.laurel;

public record ExistsExpr(
    SourceRange sourceRange,
    java.lang.String name, LaurelType ty, java.util.Optional<OptionalTrigger> trigger, StmtExpr body
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.existsExpr"; }
}
