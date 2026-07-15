package org.strata.jverify.laurel;

public sealed interface EnsuresClause extends Node permits EnsuresClause.EnsuresClause_, EnsuresClause.FreeEnsuresClause, EnsuresClause.CheckedEnsuresClause {
    public record EnsuresClause_(
        SourceRange sourceRange,
        StmtExpr cond, java.util.Optional<ErrorSummary> errorMessage
    ) implements EnsuresClause {
        @Override
        public java.lang.String operationName() { return "Laurel.ensuresClause"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.ensuresClause", sourceRange());
            sexp.add($s.serialize(cond()));
            sexp.add($s.serializeOption(errorMessage(), $s::serialize));
            return sexp;
        }
    }

    public record FreeEnsuresClause(
        SourceRange sourceRange,
        StmtExpr cond, java.util.Optional<ErrorSummary> errorMessage
    ) implements EnsuresClause {
        @Override
        public java.lang.String operationName() { return "Laurel.freeEnsuresClause"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.freeEnsuresClause", sourceRange());
            sexp.add($s.serialize(cond()));
            sexp.add($s.serializeOption(errorMessage(), $s::serialize));
            return sexp;
        }
    }

    public record CheckedEnsuresClause(
        SourceRange sourceRange,
        StmtExpr cond, java.util.Optional<ErrorSummary> errorMessage
    ) implements EnsuresClause {
        @Override
        public java.lang.String operationName() { return "Laurel.checkedEnsuresClause"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.checkedEnsuresClause", sourceRange());
            sexp.add($s.serialize(cond()));
            sexp.add($s.serializeOption(errorMessage(), $s::serialize));
            return sexp;
        }
    }
}
