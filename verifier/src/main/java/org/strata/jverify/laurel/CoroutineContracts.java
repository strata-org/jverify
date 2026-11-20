package org.strata.jverify.laurel;

public sealed interface CoroutineContracts extends ToIon permits CoroutineContracts.Regular, CoroutineContracts.Coroutine {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Regular() implements CoroutineContracts {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Regular"));

        return sexp;
        }
    }

    public record Coroutine(java.util.List<Condition> relies, java.util.List<Condition> guarantees, java.util.List<Parameter> yields, java.util.List<Parameter> resumes) implements CoroutineContracts {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Coroutine"));
        var _l0 = ion.newEmptyList();
        for (var e : relies()) _l0.add(e.toIon(ion));
        sexp.add(_l0);
        var _l1 = ion.newEmptyList();
        for (var e : guarantees()) _l1.add(e.toIon(ion));
        sexp.add(_l1);
        var _l2 = ion.newEmptyList();
        for (var e : yields()) _l2.add(e.toIon(ion));
        sexp.add(_l2);
        var _l3 = ion.newEmptyList();
        for (var e : resumes()) _l3.add(e.toIon(ion));
        sexp.add(_l3);
        return sexp;
        }
    }
}
