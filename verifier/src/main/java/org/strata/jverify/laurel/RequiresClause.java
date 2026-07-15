package org.strata.jverify.laurel;

public sealed interface RequiresClause extends Node permits RequiresClause.RequiresClause_, RequiresClause.FreeRequiresClause, RequiresClause.CheckedRequiresClause {
    public record RequiresClause_(
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

    public record FreeRequiresClause(
        SourceRange sourceRange,
        StmtExpr cond, java.util.Optional<ErrorSummary> errorMessage
    ) implements RequiresClause {
        @Override
        public java.lang.String operationName() { return "Laurel.freeRequiresClause"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.freeRequiresClause", sourceRange());
            sexp.add($s.serialize(cond()));
            sexp.add($s.serializeOption(errorMessage(), $s::serialize));
            return sexp;
        }
    }

    public record CheckedRequiresClause(
        SourceRange sourceRange,
        StmtExpr cond, java.util.Optional<ErrorSummary> errorMessage
    ) implements RequiresClause {
        @Override
        public java.lang.String operationName() { return "Laurel.checkedRequiresClause"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.checkedRequiresClause", sourceRange());
            sexp.add($s.serialize(cond()));
            sexp.add($s.serializeOption(errorMessage(), $s::serialize));
            return sexp;
        }
    }
}
