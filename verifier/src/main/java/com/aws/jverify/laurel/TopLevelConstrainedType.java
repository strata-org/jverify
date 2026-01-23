package com.aws.jverify.laurel;

public record TopLevelConstrainedType(
    SourceRange sourceRange,
    ConstrainedType ct
) implements TopLevel {
    @Override
    public java.lang.String operationName() { return "Laurel.topLevelConstrainedType"; }
}
