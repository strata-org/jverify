package com.aws.jverify.laurel;

public sealed interface InvariantClause extends Node permits InvariantClause.Of {
    public record Of(
        SourceRange sourceRange,
        StmtExpr cond
    ) implements InvariantClause {
        @Override
        public java.lang.String operationName() { return "Laurel.invariantClause"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.invariantClause", sourceRange());
            sexp.add($s.serialize(cond()));
            return sexp;
        }
    }
}
