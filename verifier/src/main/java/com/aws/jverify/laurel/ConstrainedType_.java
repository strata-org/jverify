package com.aws.jverify.laurel;

public record ConstrainedType_(
    SourceRange sourceRange,
    java.lang.String name, java.lang.String valueName, LaurelType base, StmtExpr constraint, StmtExpr witness
) implements ConstrainedType {
    @Override
    public java.lang.String operationName() { return "Laurel.constrainedType"; }
}
