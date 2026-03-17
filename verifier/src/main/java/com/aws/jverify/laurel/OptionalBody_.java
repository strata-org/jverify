package com.aws.jverify.laurel;

public record OptionalBody_(
    SourceRange sourceRange,
    StmtExpr body
) implements OptionalBody {
    @Override
    public java.lang.String operationName() { return "Laurel.optionalBody"; }
}
