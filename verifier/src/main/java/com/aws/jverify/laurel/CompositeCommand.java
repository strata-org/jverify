package com.aws.jverify.laurel;

public record CompositeCommand(
    SourceRange sourceRange,
    Composite composite
) implements Command {
    @Override
    public java.lang.String operationName() { return "Laurel.compositeCommand"; }
}
