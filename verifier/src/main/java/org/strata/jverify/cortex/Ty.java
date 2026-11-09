package org.strata.jverify.cortex;

public sealed interface Ty extends ToIon permits Ty.Int, Ty.Bool, Ty.String_, Ty.Real, Ty.Bv, Ty.Named {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Int() implements Ty {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("int"));

        return sexp;
        }
    }

    public record Bool() implements Ty {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("bool"));

        return sexp;
        }
    }

    public record String_() implements Ty {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("string"));

        return sexp;
        }
    }

    public record Real() implements Ty {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("real"));

        return sexp;
        }
    }

    public record Bv(long width) implements Ty {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("bv"));
        sexp.add(ion.newInt(width()));
        return sexp;
        }
    }

    public record Named(java.lang.String name) implements Ty {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("named"));
        sexp.add(ion.newString(name()));
        return sexp;
        }
    }
}
