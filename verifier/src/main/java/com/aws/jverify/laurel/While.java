package com.aws.jverify.laurel;

public record While(
    SourceRange sourceRange,
    StmtExpr cond, java.util.List<InvariantClause> invariants, StmtExpr body
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.while"; }
}
