package org.strata.jverify.laurel;

public sealed interface DatatypeConstructor permits DatatypeConstructor.DatatypeConstructor_, DatatypeConstructor.DatatypeConstructorNoArgs {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record DatatypeConstructor_(SourceRange sourceRange, java.lang.String name, java.util.List<DatatypeConstructorArg> args) implements DatatypeConstructor {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("datatypeConstructor"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newString(name));
            var _l = ion.newEmptyList(); for (var e : args) _l.add(e.toIon(ion));
            sexp.add(_l); return sexp;
        }
    }

    public record DatatypeConstructorNoArgs(SourceRange sourceRange, java.lang.String name) implements DatatypeConstructor {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("datatypeConstructorNoArgs"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newString(name)); return sexp;
        }
    }
}
