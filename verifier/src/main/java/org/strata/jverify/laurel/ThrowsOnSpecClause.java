package org.strata.jverify.laurel;

public sealed interface ThrowsOnSpecClause extends Node permits ThrowsOnSpecClause.ThrowsOnEnsures, ThrowsOnSpecClause.ThrowsOnModifies {
    public record ThrowsOnEnsures(
        SourceRange sourceRange,
        StmtExpr cond, java.util.Optional<ErrorSummary> errorMessage
    ) implements ThrowsOnSpecClause {
        @Override
        public java.lang.String operationName() { return "Laurel.throwsOnEnsures"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.throwsOnEnsures", sourceRange());
            sexp.add($s.serialize(cond()));
            sexp.add($s.serializeOption(errorMessage(), $s::serialize));
            return sexp;
        }
    }

    public record ThrowsOnModifies(
        SourceRange sourceRange,
        java.util.List<StmtExpr> refs
    ) implements ThrowsOnSpecClause {
        @Override
        public java.lang.String operationName() { return "Laurel.throwsOnModifies"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.throwsOnModifies", sourceRange());
            sexp.add($s.serializeSeq(refs(), "commaSepList", $s::serialize));
            return sexp;
        }
    }
}
