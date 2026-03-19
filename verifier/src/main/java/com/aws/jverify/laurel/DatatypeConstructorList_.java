package com.aws.jverify.laurel;

public record DatatypeConstructorList_(
    SourceRange sourceRange,
    java.util.List<DatatypeConstructor> constructors
) implements DatatypeConstructorList {
    @Override
    public java.lang.String operationName() { return "Laurel.datatypeConstructorList"; }
}
