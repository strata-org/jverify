package com.aws.jverify.laurel;

public record Parameter_(
    SourceRange sourceRange,
    java.lang.String name, LaurelType paramType
) implements Parameter {
    @Override
    public java.lang.String operationName() { return "Laurel.parameter"; }
}
