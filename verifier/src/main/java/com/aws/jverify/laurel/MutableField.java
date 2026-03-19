package com.aws.jverify.laurel;

public record MutableField(
    SourceRange sourceRange,
    java.lang.String name, LaurelType fieldType
) implements Field {
    @Override
    public java.lang.String operationName() { return "Laurel.mutableField"; }
}
