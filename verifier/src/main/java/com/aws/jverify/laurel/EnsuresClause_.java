package com.aws.jverify.laurel;

public record EnsuresClause_(
    SourceRange sourceRange,
    StmtExpr cond
) implements EnsuresClause {
    @Override
    public java.lang.String operationName() { return "Laurel.ensuresClause"; }
}
