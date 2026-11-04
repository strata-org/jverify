package org.strata.jverify.laurel;

public sealed interface GotoDestination extends ToIon permits GotoDestination.BlockStart, GotoDestination.BlockEnd {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record BlockStart() implements GotoDestination {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("blockStart"));

        return sexp;
        }
    }

    public record BlockEnd() implements GotoDestination {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("blockEnd"));

        return sexp;
        }
    }
}
