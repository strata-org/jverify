package org.strata.jverify.laurel;

public sealed interface TypeParams extends Node permits TypeParams.Of {
    public record Of(
        SourceRange sourceRange,
        java.util.List<java.lang.String> params
    ) implements TypeParams {
        @Override
        public java.lang.String operationName() { return "Laurel.typeParams"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.typeParams", sourceRange());
            sexp.add($s.serializeSeq(params(), "commaSepList", $s::serializeIdent));
            return sexp;
        }
    }
}
