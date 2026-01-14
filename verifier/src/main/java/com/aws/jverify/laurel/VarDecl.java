package com.aws.jverify.laurel;

public record VarDecl(
    SourceRange sourceRange,
    java.lang.String name, java.util.Optional<OptionalType> varType, java.util.Optional<OptionalAssignment> assignment
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.varDecl"; }
}
