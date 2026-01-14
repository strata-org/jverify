package com.aws.jverify.laurel;

public record IntType(
    SourceRange sourceRange
) implements LaurelType {
    @Override
    public java.lang.String operationName() { return "Laurel.intType"; }
}
