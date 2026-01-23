package com.aws.jverify.laurel;

public record RequiresClause_(
    SourceRange sourceRange,
    StmtExpr cond
) implements RequiresClause {
    @Override
    public java.lang.String operationName() { return "Laurel.requiresClause"; }
}
