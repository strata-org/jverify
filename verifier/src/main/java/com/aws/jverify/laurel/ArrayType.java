package com.aws.jverify.laurel;

public record ArrayType(
    SourceRange sourceRange,
    LaurelType elemType
) implements LaurelType {
    @Override
    public java.lang.String operationName() { return "Laurel.arrayType"; }
}
