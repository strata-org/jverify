package org.strata.jverify.laurel;

public sealed interface ThrowsOnClause extends Node permits ThrowsOnClause.Of {
    public record Of(
        SourceRange sourceRange,
        StmtExpr guard, java.util.List<ThrowsOnSpecClause> clauses
    ) implements ThrowsOnClause {
        @Override
        public java.lang.String operationName() { return "Laurel.throwsOnClause"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.throwsOnClause", sourceRange());
            sexp.add($s.serialize(guard()));
            sexp.add($s.serializeSeq(clauses(), "seq", $s::serialize));
            return sexp;
        }
    }
}
