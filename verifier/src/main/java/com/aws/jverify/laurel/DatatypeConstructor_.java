package com.aws.jverify.laurel;

public record DatatypeConstructor_(
    SourceRange sourceRange,
    java.lang.String name, java.util.List<DatatypeConstructorArg> args
) implements DatatypeConstructor {
    @Override
    public java.lang.String operationName() { return "Laurel.datatypeConstructor"; }
}
