package com.aws.jverify.laurel;

public sealed interface ReturnType extends Node permits ReturnType.Of {
    public record Of(
        SourceRange sourceRange,
        LaurelType returnType
    ) implements ReturnType {
        @Override
        public java.lang.String operationName() { return "Laurel.returnType"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.returnType", sourceRange());
            sexp.add($s.serialize(returnType()));
            return sexp;
        }
    }
}
