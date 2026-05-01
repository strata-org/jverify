package org.strata.jverify.laurel;

public sealed interface DatatypeConstructor extends Node permits DatatypeConstructor.DatatypeConstructor_, DatatypeConstructor.DatatypeConstructorNoArgs {
    public record DatatypeConstructor_(
        SourceRange sourceRange,
        java.lang.String name, java.util.List<DatatypeConstructorArg> args
    ) implements DatatypeConstructor {
        @Override
        public java.lang.String operationName() { return "Laurel.datatypeConstructor"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.datatypeConstructor", sourceRange());
            sexp.add($s.serializeIdent(name()));
            sexp.add($s.serializeSeq(args(), "commaSepList", $s::serialize));
            return sexp;
        }
    }

    public record DatatypeConstructorNoArgs(
        SourceRange sourceRange,
        java.lang.String name
    ) implements DatatypeConstructor {
        @Override
        public java.lang.String operationName() { return "Laurel.datatypeConstructorNoArgs"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.datatypeConstructorNoArgs", sourceRange());
            sexp.add($s.serializeIdent(name()));
            return sexp;
        }
    }
}
