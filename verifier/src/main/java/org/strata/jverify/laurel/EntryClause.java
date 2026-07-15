package org.strata.jverify.laurel;

public sealed interface EntryClause extends Node permits EntryClause.Of {
    public record Of(
        SourceRange sourceRange
    ) implements EntryClause {
        @Override
        public java.lang.String operationName() { return "Laurel.entryClause"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.entryClause", sourceRange());
            return sexp;
        }
    }
}
