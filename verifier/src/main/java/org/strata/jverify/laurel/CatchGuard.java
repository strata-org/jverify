package org.strata.jverify.laurel;

public sealed interface CatchGuard extends Node permits CatchGuard.Of {
    public record Of(
        SourceRange sourceRange,
        StmtExpr predicate
    ) implements CatchGuard {
        @Override
        public java.lang.String operationName() { return "Laurel.catchGuard"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.catchGuard", sourceRange());
            sexp.add($s.serialize(predicate()));
            return sexp;
        }
    }
}
