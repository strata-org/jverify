package com.aws.jverify.laurel;

public sealed interface Initializer extends Node permits Initializer.Of {
    public record Of(
        SourceRange sourceRange,
        StmtExpr value
    ) implements Initializer {
        @Override
        public java.lang.String operationName() { return "Laurel.initializer"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.initializer", sourceRange());
            sexp.add($s.serialize(value()));
            return sexp;
        }
    }
}
