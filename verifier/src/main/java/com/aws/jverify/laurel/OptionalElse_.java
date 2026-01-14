package com.aws.jverify.laurel;

public record OptionalElse_(
    SourceRange sourceRange,
    StmtExpr stmts
) implements OptionalElse {
    @Override
    public java.lang.String operationName() { return "Laurel.optionalElse"; }
}
