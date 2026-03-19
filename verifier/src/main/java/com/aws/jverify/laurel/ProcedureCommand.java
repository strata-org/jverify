package com.aws.jverify.laurel;

public record ProcedureCommand(
    SourceRange sourceRange,
    Procedure procedure
) implements Command {
    @Override
    public java.lang.String operationName() { return "Laurel.procedureCommand"; }
}
