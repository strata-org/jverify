package org.strata.jverify.laurel;

public sealed interface ThrowsClause extends Node permits ThrowsClause.Of {
    public record Of(
        SourceRange sourceRange,
        java.lang.String binding, LaurelType throwsType
    ) implements ThrowsClause {
        @Override
        public java.lang.String operationName() { return "Laurel.throwsClause"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.throwsClause", sourceRange());
            sexp.add($s.serializeIdent(binding()));
            sexp.add($s.serialize(throwsType()));
            return sexp;
        }
    }
}
