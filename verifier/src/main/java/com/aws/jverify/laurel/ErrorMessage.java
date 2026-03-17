package com.aws.jverify.laurel;

public record ErrorMessage(
    SourceRange sourceRange,
    java.lang.String msg
) implements OptionalErrorMessage {
    @Override
    public java.lang.String operationName() { return "Laurel.errorMessage"; }
}
