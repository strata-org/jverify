package com.aws.jverify.laurel;

public record TopLevelProcedure(
    SourceRange sourceRange,
    Procedure procedure
) implements TopLevel {
    @Override
    public java.lang.String operationName() { return "Laurel.topLevelProcedure"; }
}
