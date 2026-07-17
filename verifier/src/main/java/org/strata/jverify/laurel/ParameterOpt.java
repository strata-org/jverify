package org.strata.jverify.laurel;

public record ParameterOpt(Identifier name, java.util.Optional<AstNode<HighType>> type) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", name().toIon(ion));
        s.put("type", (type().isPresent() ? type().get().toIon(ion) : ion.newNull()));
        return s;
    }
}
