package org.strata.jverify.laurel;

public sealed interface CatchClause extends Node permits CatchClause.Of {
    public record Of(
        SourceRange sourceRange,
        java.lang.String binding, java.util.Optional<CatchGuard> guard, StmtExpr body
    ) implements CatchClause {
        @Override
        public java.lang.String operationName() { return "Laurel.catchClause"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.catchClause", sourceRange());
            sexp.add($s.serializeIdent(binding()));
            sexp.add($s.serializeOption(guard(), $s::serialize));
            sexp.add($s.serialize(body()));
            return sexp;
        }
    }
}
