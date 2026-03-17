package com.aws.jverify.laurel;

public record ModifiesClause_(
    SourceRange sourceRange,
    java.util.List<StmtExpr> refs
) implements ModifiesClause {
    @Override
    public java.lang.String operationName() { return "Laurel.modifiesClause"; }
}
