package com.aws.jverify.laurel;

public record RealType(
    SourceRange sourceRange
) implements LaurelType {
    @Override
    public java.lang.String operationName() { return "Laurel.realType"; }
}
