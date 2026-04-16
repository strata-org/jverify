package com.aws.jverify.laurel;

public sealed interface Body extends Node permits Body.Body_, Body.ExternalBody {
    public record Body_(
        SourceRange sourceRange,
        StmtExpr body
    ) implements Body {
        @Override
        public java.lang.String operationName() { return "Laurel.body"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.body", sourceRange());
            sexp.add($s.serialize(body()));
            return sexp;
        }
    }

    public record ExternalBody(
        SourceRange sourceRange
    ) implements Body {
        @Override
        public java.lang.String operationName() { return "Laurel.externalBody"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.externalBody", sourceRange());
            return sexp;
        }
    }
}
