package com.aws.jverify.laurel;

public record ReturnParameters_(
    SourceRange sourceRange,
    java.util.List<Parameter> parameters
) implements ReturnParameters {
    @Override
    public java.lang.String operationName() { return "Laurel.returnParameters"; }
}
