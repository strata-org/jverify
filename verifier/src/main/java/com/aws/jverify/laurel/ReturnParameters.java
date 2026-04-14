package com.aws.jverify.laurel;

public sealed interface ReturnParameters extends Node permits ReturnParameters.Of {
    public record Of(
        SourceRange sourceRange,
        java.util.List<Parameter> parameters
    ) implements ReturnParameters {
        @Override
        public java.lang.String operationName() { return "Laurel.returnParameters"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.returnParameters", sourceRange());
            sexp.add($s.serializeSeq(parameters(), "commaSepList", $s::serialize));
            return sexp;
        }
    }
}
