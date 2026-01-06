package com.aws.jverify.laurel;

public record Program(
    SourceRange sourceRange,
    java.util.List<Procedure> staticProcedures
) implements Command {
    @Override
    public java.lang.String operationName() { return "Laurel.program"; }
}
