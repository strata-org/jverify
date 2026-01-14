package com.aws.jverify.laurel;

public record OptionalAssignment_(
    SourceRange sourceRange,
    StmtExpr value
) implements OptionalAssignment {
    @Override
    public java.lang.String operationName() { return "Laurel.optionalAssignment"; }
}
