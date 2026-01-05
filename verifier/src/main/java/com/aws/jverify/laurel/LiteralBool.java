package com.aws.jverify.laurel;

public record LiteralBool(
    SourceRange sourceRange,
    boolean b
) implements StmtExpr {
    @Override
    public java.lang.String operationName() { return "Laurel.literalBool"; }
}
