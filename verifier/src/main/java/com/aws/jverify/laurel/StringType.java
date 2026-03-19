package com.aws.jverify.laurel;

public record StringType(
    SourceRange sourceRange
) implements LaurelType {
    @Override
    public java.lang.String operationName() { return "Laurel.stringType"; }
}
