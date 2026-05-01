package org.strata.jverify.laurel;

public sealed interface DatatypeConstructorList extends Node permits DatatypeConstructorList.Of {
    public record Of(
        SourceRange sourceRange,
        java.util.List<DatatypeConstructor> constructors
    ) implements DatatypeConstructorList {
        @Override
        public java.lang.String operationName() { return "Laurel.datatypeConstructorList"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.datatypeConstructorList", sourceRange());
            sexp.add($s.serializeSeq(constructors(), "commaSepList", $s::serialize));
            return sexp;
        }
    }
}
