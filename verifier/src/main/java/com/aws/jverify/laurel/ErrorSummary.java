package com.aws.jverify.laurel;

public sealed interface ErrorSummary extends Node permits ErrorSummary.Of {
    public record Of(
        SourceRange sourceRange,
        java.lang.String msg
    ) implements ErrorSummary {
        @Override
        public java.lang.String operationName() { return "Laurel.errorSummary"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.errorSummary", sourceRange());
            sexp.add($s.serializeStrlit(msg()));
            return sexp;
        }
    }
}
