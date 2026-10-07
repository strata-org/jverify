package org.strata.jverify.laurel;

public sealed interface ContractType extends ToIon permits ContractType.Reads, ContractType.Modifies, ContractType.Precondition, ContractType.PostCondition, ContractType.Relies, ContractType.Guarantees {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Reads() implements ContractType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Reads"));

        return sexp;
        }
    }

    public record Modifies() implements ContractType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Modifies"));

        return sexp;
        }
    }

    public record Precondition() implements ContractType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Precondition"));

        return sexp;
        }
    }

    public record PostCondition() implements ContractType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("PostCondition"));

        return sexp;
        }
    }

    public record Relies() implements ContractType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Relies"));

        return sexp;
        }
    }

    public record Guarantees() implements ContractType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Guarantees"));

        return sexp;
        }
    }
}
