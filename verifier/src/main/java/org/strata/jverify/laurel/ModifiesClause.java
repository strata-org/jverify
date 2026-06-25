package org.strata.jverify.laurel;

public sealed interface ModifiesClause extends Node permits ModifiesClause.ModifiesClause_, ModifiesClause.ModifiesWildcard {
    public record ModifiesClause_(
        SourceRange sourceRange,
        java.util.List<StmtExpr> refs
    ) implements ModifiesClause {
        @Override
        public java.lang.String operationName() { return "Laurel.modifiesClause"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.modifiesClause", sourceRange());
            sexp.add($s.serializeSeq(refs(), "commaSepList", $s::serialize));
            return sexp;
        }
    }

    public record ModifiesWildcard(
        SourceRange sourceRange
    ) implements ModifiesClause {
        @Override
        public java.lang.String operationName() { return "Laurel.modifiesWildcard"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.modifiesWildcard", sourceRange());
            return sexp;
        }
    }
}
