package com.aws.jverify.laurel;

public record IsType(
    SourceRange sourceRange,
    StmtExpr target, java.lang.String typeName
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.isType"; }
}
