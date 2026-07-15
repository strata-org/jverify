package org.strata.jverify.laurel;

public sealed interface Procedure extends Node permits Procedure.Of {
    public record Of(
        SourceRange sourceRange,
        java.lang.String name, java.util.List<Parameter> parameters, java.util.Optional<ReturnType> returnType, java.util.Optional<ReturnParameters> returnParameters, java.util.List<RequiresClause> requires, java.util.Optional<InvokeOnClause> invokeOn, java.util.Optional<EntryClause> entry, java.util.Optional<OpaqueSpec> opaqueSpec, java.util.Optional<Body> body
    ) implements Procedure {
        @Override
        public java.lang.String operationName() { return "Laurel.procedure"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.procedure", sourceRange());
            sexp.add($s.serializeIdent(name()));
            sexp.add($s.serializeSeq(parameters(), "commaSepList", $s::serialize));
            sexp.add($s.serializeOption(returnType(), $s::serialize));
            sexp.add($s.serializeOption(returnParameters(), $s::serialize));
            sexp.add($s.serializeSeq(requires(), "seq", $s::serialize));
            sexp.add($s.serializeOption(invokeOn(), $s::serialize));
            sexp.add($s.serializeOption(entry(), $s::serialize));
            sexp.add($s.serializeOption(opaqueSpec(), $s::serialize));
            sexp.add($s.serializeOption(body(), $s::serialize));
            return sexp;
        }
    }
}
