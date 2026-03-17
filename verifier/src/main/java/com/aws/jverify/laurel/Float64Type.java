package com.aws.jverify.laurel;

public record Float64Type(
    SourceRange sourceRange
) implements LaurelType {
    @Override
    public java.lang.String operationName() { return "Laurel.float64Type"; }
}
