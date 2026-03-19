package com.aws.jverify.laurel;

public record MapType(
    SourceRange sourceRange,
    LaurelType keyType, LaurelType valueType
) implements LaurelType {
    @Override
    public java.lang.String operationName() { return "Laurel.mapType"; }
}
