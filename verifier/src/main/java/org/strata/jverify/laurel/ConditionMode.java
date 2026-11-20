package org.strata.jverify.laurel;

public sealed interface ConditionMode extends ToIon permits ConditionMode.Assert, ConditionMode.Assume, ConditionMode.Both {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Assert() implements ConditionMode {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Assert"));

        return sexp;
        }
    }

    public record Assume() implements ConditionMode {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Assume"));

        return sexp;
        }
    }

    public record Both() implements ConditionMode {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Both"));

        return sexp;
        }
    }
}
