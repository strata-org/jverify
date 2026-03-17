package com.aws.jverify.laurel;

public record OrElse(
    SourceRange sourceRange,
    StmtExpr lhs, StmtExpr rhs
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.orElse"; }
}
