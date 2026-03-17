package com.aws.jverify.laurel;

public record DatatypeConstructorNoArgs(
    SourceRange sourceRange,
    java.lang.String name
) implements DatatypeConstructor {
    @Override
    public java.lang.String operationName() { return "Laurel.datatypeConstructorNoArgs"; }
}
