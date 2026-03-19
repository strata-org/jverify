package com.aws.jverify.laurel;

public record OptionalExtends_(
    SourceRange sourceRange,
    java.util.List<java.lang.String> parents
) implements OptionalExtends {
    @Override
    public java.lang.String operationName() { return "Laurel.optionalExtends"; }
}
