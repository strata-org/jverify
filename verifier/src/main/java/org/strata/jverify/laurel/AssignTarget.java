package org.strata.jverify.laurel;

public sealed interface AssignTarget extends Node permits AssignTarget.AssignTargetDecl, AssignTarget.AssignTargetVar, AssignTarget.AssignTargetField {
    public record AssignTargetDecl(
        SourceRange sourceRange,
        java.lang.String name, LaurelType targetType
    ) implements AssignTarget {
        @Override
        public java.lang.String operationName() { return "Laurel.assignTargetDecl"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.assignTargetDecl", sourceRange());
            sexp.add($s.serializeIdent(name()));
            sexp.add($s.serialize(targetType()));
            return sexp;
        }
    }

    public record AssignTargetVar(
        SourceRange sourceRange,
        java.lang.String name
    ) implements AssignTarget {
        @Override
        public java.lang.String operationName() { return "Laurel.assignTargetVar"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.assignTargetVar", sourceRange());
            sexp.add($s.serializeIdent(name()));
            return sexp;
        }
    }

    public record AssignTargetField(
        SourceRange sourceRange,
        java.lang.String obj, java.lang.String field
    ) implements AssignTarget {
        @Override
        public java.lang.String operationName() { return "Laurel.assignTargetField"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.assignTargetField", sourceRange());
            sexp.add($s.serializeIdent(obj()));
            sexp.add($s.serializeIdent(field()));
            return sexp;
        }
    }
}
