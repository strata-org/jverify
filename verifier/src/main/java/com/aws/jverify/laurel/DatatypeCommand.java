package com.aws.jverify.laurel;

public record DatatypeCommand(
    SourceRange sourceRange,
    Datatype datatype
) implements Command {
    @Override
    public java.lang.String operationName() { return "Laurel.datatypeCommand"; }
}
