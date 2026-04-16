package com.aws.jverify.laurel;

public sealed interface InvokeOnClause extends Node permits InvokeOnClause.Of {
    public record Of(
        SourceRange sourceRange,
        StmtExpr trigger
    ) implements InvokeOnClause {
        @Override
        public java.lang.String operationName() { return "Laurel.invokeOnClause"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.invokeOnClause", sourceRange());
            sexp.add($s.serialize(trigger()));
            return sexp;
        }
    }
}
