package org.strata.jverify.laurel;

public sealed interface RequiresClause extends Node permits RequiresClause.Of {
    public record Of(
        SourceRange sourceRange,
        StmtExpr cond, java.util.Optional<ErrorSummary> errorMessage
    ) implements RequiresClause {
        @Override
        public java.lang.String operationName() { return "Laurel.requiresClause"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.requiresClause", sourceRange());
            sexp.add($s.serialize(cond()));
            sexp.add($s.serializeOption(errorMessage(), $s::serialize));
            return sexp;
        }
    }
}
