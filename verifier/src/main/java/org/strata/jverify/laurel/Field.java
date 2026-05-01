package org.strata.jverify.laurel;

public sealed interface Field permits Field.MutableField, Field.ImmutableField {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record MutableField(SourceRange sourceRange, java.lang.String name, LaurelType fieldType) implements Field {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("mutableField"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newString(name)); sexp.add(fieldType.toIon(ion)); return sexp;
        }
    }

    public record ImmutableField(SourceRange sourceRange, java.lang.String name, LaurelType fieldType) implements Field {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("immutableField"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newString(name)); sexp.add(fieldType.toIon(ion)); return sexp;
        }
    }
}
