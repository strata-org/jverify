package com.aws.jverify.laurel;

public record TopLevelComposite(
    SourceRange sourceRange,
    Composite composite
) implements TopLevel {
    @Override
    public java.lang.String operationName() { return "Laurel.topLevelComposite"; }
}
