package com.aws.jverify.laurel;

public record ExternalBody(
    SourceRange sourceRange
) implements OptionalBody {
    @Override
    public java.lang.String operationName() { return "Laurel.externalBody"; }
}
