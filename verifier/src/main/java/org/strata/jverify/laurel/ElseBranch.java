package org.strata.jverify.laurel;

public sealed interface ElseBranch extends Node permits ElseBranch.Of {
    public record Of(
        SourceRange sourceRange,
        StmtExpr stmts
    ) implements ElseBranch {
        @Override
        public java.lang.String operationName() { return "Laurel.elseBranch"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.elseBranch", sourceRange());
            sexp.add($s.serialize(stmts()));
            return sexp;
        }
    }
}
