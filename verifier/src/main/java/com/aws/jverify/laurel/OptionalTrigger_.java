package com.aws.jverify.laurel;

public record OptionalTrigger_(
    SourceRange sourceRange,
    StmtExpr trigger
) implements OptionalTrigger {
    @Override
    public java.lang.String operationName() { return "Laurel.optionalTrigger"; }
}
