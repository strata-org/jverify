package com.aws.jverify.laurel;

public record IfThenElse(
    SourceRange sourceRange,
    StmtExpr cond, StmtExpr thenBranch, java.util.Optional<OptionalElse> elseBranch
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.ifThenElse"; }
}
