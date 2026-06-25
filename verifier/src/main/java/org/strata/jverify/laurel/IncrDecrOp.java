package org.strata.jverify.laurel;

public sealed interface IncrDecrOp permits IncrDecrOp.Incr, IncrDecrOp.Decr {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Incr() implements IncrDecrOp {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Incr"));

        return sexp;
        }
    }

    public record Decr() implements IncrDecrOp {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Decr"));

        return sexp;
        }
    }
}
