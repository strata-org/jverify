package com.aws.jverify.laurel;

public record ConstrainedTypeCommand(
    SourceRange sourceRange,
    ConstrainedType ct
) implements Command {
    @Override
    public java.lang.String operationName() { return "Laurel.constrainedTypeCommand"; }
}
