package org.strata.jverify.laurel;

public sealed interface Parameter extends Node permits Parameter.Of {
    public record Of(
        SourceRange sourceRange,
        java.lang.String name, LaurelType paramType
    ) implements Parameter {
        @Override
        public java.lang.String operationName() { return "Laurel.parameter"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.parameter", sourceRange());
            sexp.add($s.serializeIdent(name()));
            sexp.add($s.serialize(paramType()));
            return sexp;
        }
    }
}
