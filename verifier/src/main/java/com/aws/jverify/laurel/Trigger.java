package com.aws.jverify.laurel;

public sealed interface Trigger extends Node permits Trigger.Of {
    public record Of(
        SourceRange sourceRange,
        StmtExpr trigger
    ) implements Trigger {
        @Override
        public java.lang.String operationName() { return "Laurel.trigger"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.trigger", sourceRange());
            sexp.add($s.serialize(trigger()));
            return sexp;
        }
    }
}
