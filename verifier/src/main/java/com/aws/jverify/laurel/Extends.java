package com.aws.jverify.laurel;

public sealed interface Extends extends Node permits Extends.Of {
    public record Of(
        SourceRange sourceRange,
        java.util.List<java.lang.String> parents
    ) implements Extends {
        @Override
        public java.lang.String operationName() { return "Laurel.extends"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.extends", sourceRange());
            sexp.add($s.serializeSeq(parents(), "commaSepList", $s::serializeIdent));
            return sexp;
        }
    }
}
