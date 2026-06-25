package org.strata.jverify.laurel;

public sealed interface QuantifierMode extends ToIon permits QuantifierMode.Forall, QuantifierMode.Exists {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Forall() implements QuantifierMode {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Forall"));

        return sexp;
        }
    }

    public record Exists() implements QuantifierMode {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Exists"));

        return sexp;
        }
    }
}
