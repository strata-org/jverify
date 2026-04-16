package com.aws.jverify.laurel;

public sealed interface TypeAnnotation extends Node permits TypeAnnotation.Of {
    public record Of(
        SourceRange sourceRange,
        LaurelType varType
    ) implements TypeAnnotation {
        @Override
        public java.lang.String operationName() { return "Laurel.typeAnnotation"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.typeAnnotation", sourceRange());
            sexp.add($s.serialize(varType()));
            return sexp;
        }
    }
}
