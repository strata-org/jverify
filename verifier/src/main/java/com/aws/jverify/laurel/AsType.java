package com.aws.jverify.laurel;

public record AsType(
    SourceRange sourceRange,
    StmtExpr target, java.lang.String typeName
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.asType"; }
}
