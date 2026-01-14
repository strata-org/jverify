package com.aws.jverify.laurel;

public record OptionalType_(
    SourceRange sourceRange,
    LaurelType varType
) implements OptionalType {
    @Override
    public java.lang.String operationName() { return "Laurel.optionalType"; }
}
