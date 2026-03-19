package com.aws.jverify.laurel;

public record RequiresClause_(
    SourceRange sourceRange,
    StmtExpr cond, java.util.Optional<OptionalErrorMessage> errorMessage
) implements RequiresClause {
    @Override
    public java.lang.String operationName() { return "Laurel.requiresClause"; }
}
