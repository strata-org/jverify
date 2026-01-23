package com.aws.jverify.laurel;

public record InvariantClause_(
    SourceRange sourceRange,
    StmtExpr cond
) implements InvariantClause {
    @Override
    public java.lang.String operationName() { return "Laurel.invariantClause"; }
}
