package com.aws.jverify.laurel;

public record DatatypeConstructorArg_(
    SourceRange sourceRange,
    java.lang.String name, LaurelType argType
) implements DatatypeConstructorArg {
    @Override
    public java.lang.String operationName() { return "Laurel.datatypeConstructorArg"; }
}
