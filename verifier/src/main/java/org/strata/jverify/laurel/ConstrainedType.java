package org.strata.jverify.laurel;

public sealed interface ConstrainedType extends Node permits ConstrainedType.Of {
    public record Of(
        SourceRange sourceRange,
        java.lang.String name, java.lang.String valueName, LaurelType base, StmtExpr constraint, StmtExpr witness
    ) implements ConstrainedType {
        @Override
        public java.lang.String operationName() { return "Laurel.constrainedType"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.constrainedType", sourceRange());
            sexp.add($s.serializeIdent(name()));
            sexp.add($s.serializeIdent(valueName()));
            sexp.add($s.serialize(base()));
            sexp.add($s.serialize(constraint()));
            sexp.add($s.serialize(witness()));
            return sexp;
        }
    }
}
