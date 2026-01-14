package com.aws.jverify.laurel;

public record BoolType(
    SourceRange sourceRange
) implements LaurelType {
    @Override
    public java.lang.String operationName() { return "Laurel.boolType"; }
}
