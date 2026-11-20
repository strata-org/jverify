package org.strata.jverify.laurel;

public sealed interface IncrDecrMode extends ToIon permits IncrDecrMode.Pre, IncrDecrMode.Post {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Pre() implements IncrDecrMode {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Pre"));

        return sexp;
        }
    }

    public record Post() implements IncrDecrMode {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Post"));

        return sexp;
        }
    }
}
