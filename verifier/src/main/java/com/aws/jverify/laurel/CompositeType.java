package com.aws.jverify.laurel;

public record CompositeType(
    SourceRange sourceRange,
    java.lang.String name
) implements LaurelType {
    @Override
    public java.lang.String operationName() { return "Laurel.compositeType"; }
}
