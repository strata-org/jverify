package com.aws.jverify.laurel;

public record Datatype_(
    SourceRange sourceRange,
    java.lang.String name, DatatypeConstructorList constructors
) implements Datatype {
    @Override
    public java.lang.String operationName() { return "Laurel.datatype"; }
}
