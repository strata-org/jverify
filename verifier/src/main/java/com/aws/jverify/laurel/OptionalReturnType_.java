package com.aws.jverify.laurel;

public record OptionalReturnType_(
    SourceRange sourceRange,
    LaurelType returnType
) implements OptionalReturnType {
    @Override
    public java.lang.String operationName() { return "Laurel.optionalReturnType"; }
}
