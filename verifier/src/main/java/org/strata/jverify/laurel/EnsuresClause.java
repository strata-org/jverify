package org.strata.jverify.laurel;

public sealed interface EnsuresClause extends Node permits EnsuresClause.Of {
    public record Of(
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
}
