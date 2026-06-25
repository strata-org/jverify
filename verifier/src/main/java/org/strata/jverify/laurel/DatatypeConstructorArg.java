package org.strata.jverify.laurel;

public sealed interface DatatypeConstructorArg extends Node permits DatatypeConstructorArg.Of {
    public record Of(
        SourceRange sourceRange,
        java.lang.String name, LaurelType argType
    ) implements DatatypeConstructorArg {
        @Override
        public java.lang.String operationName() { return "Laurel.datatypeConstructorArg"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.datatypeConstructorArg", sourceRange());
            sexp.add($s.serializeIdent(name()));
            sexp.add($s.serialize(argType()));
            return sexp;
        }
    }
}
