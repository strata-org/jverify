package org.strata.jverify.laurel;

public sealed interface OpaqueSpec extends Node permits OpaqueSpec.Of {
    public record Of(
        SourceRange sourceRange,
        java.util.List<EnsuresClause> ensures, java.util.List<ModifiesClause> modifies
    ) implements OpaqueSpec {
        @Override
        public java.lang.String operationName() { return "Laurel.opaqueSpec"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.opaqueSpec", sourceRange());
            sexp.add($s.serializeSeq(ensures(), "seq", $s::serialize));
            sexp.add($s.serializeSeq(modifies(), "seq", $s::serialize));
            return sexp;
        }
    }
}
