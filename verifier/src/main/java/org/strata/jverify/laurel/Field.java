package org.strata.jverify.laurel;

public sealed interface Field extends Node permits Field.MutableField, Field.ImmutableField {
    public record MutableField(
        SourceRange sourceRange,
        java.lang.String name, LaurelType fieldType
    ) implements Field {
        @Override
        public java.lang.String operationName() { return "Laurel.mutableField"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.mutableField", sourceRange());
            sexp.add($s.serializeIdent(name()));
            sexp.add($s.serialize(fieldType()));
            return sexp;
        }
    }

    public record ImmutableField(
        SourceRange sourceRange,
        java.lang.String name, LaurelType fieldType
    ) implements Field {
        @Override
        public java.lang.String operationName() { return "Laurel.immutableField"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.immutableField", sourceRange());
            sexp.add($s.serializeIdent(name()));
            sexp.add($s.serialize(fieldType()));
            return sexp;
        }
    }
}
