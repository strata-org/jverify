package com.aws.jverify.laurel;

public record Composite_(
    SourceRange sourceRange,
    java.lang.String name, java.util.Optional<OptionalExtends> extending, java.util.List<Field> fields
) implements Composite {
    @Override
    public java.lang.String operationName() { return "Laurel.composite"; }
}
