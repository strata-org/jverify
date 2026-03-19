package com.aws.jverify.laurel;

public record ForLoop(
    SourceRange sourceRange,
    StmtExpr init, StmtExpr cond, StmtExpr step, java.util.List<InvariantClause> invariants, StmtExpr body
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.forLoop"; }
}
