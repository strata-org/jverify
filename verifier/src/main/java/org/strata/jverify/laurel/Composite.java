package org.strata.jverify.laurel;

public sealed interface Composite extends Node permits Composite.Of {
    public record Of(
        SourceRange sourceRange,
        java.lang.String name, java.util.Optional<Extends> extending, java.util.List<Field> fields, java.util.List<Procedure> procedures
    ) implements Composite {
        @Override
        public java.lang.String operationName() { return "Laurel.composite"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.composite", sourceRange());
            sexp.add($s.serializeIdent(name()));
            sexp.add($s.serializeOption(extending(), $s::serialize));
            sexp.add($s.serializeSeq(fields(), "seq", $s::serialize));
            sexp.add($s.serializeSeq(procedures(), "seq", $s::serialize));
            return sexp;
        }
    }
}
