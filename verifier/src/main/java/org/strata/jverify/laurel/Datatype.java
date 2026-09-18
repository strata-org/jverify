package org.strata.jverify.laurel;

public sealed interface Datatype extends Node permits Datatype.Of {
    public record Of(
        SourceRange sourceRange,
        java.lang.String name, java.util.Optional<TypeParams> typeParams, DatatypeConstructorList constructors
    ) implements Datatype {
        @Override
        public java.lang.String operationName() { return "Laurel.datatype"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.datatype", sourceRange());
            sexp.add($s.serializeIdent(name()));
            sexp.add($s.serializeOption(typeParams(), $s::serialize));
            sexp.add($s.serialize(constructors()));
            return sexp;
        }
    }
}
