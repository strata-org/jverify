package com.aws.jverify.laurel;

public record FieldAccess(
    SourceRange sourceRange,
    StmtExpr obj, java.lang.String field
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.fieldAccess"; }
}
