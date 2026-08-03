package org.strata.jverify.laurel;

public sealed interface FinallyClause extends Node permits FinallyClause.Of {
    public record Of(
        SourceRange sourceRange,
        StmtExpr body
    ) implements FinallyClause {
        @Override
        public java.lang.String operationName() { return "Laurel.finallyClause"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.finallyClause", sourceRange());
            sexp.add($s.serialize(body()));
            return sexp;
        }
    }
}
