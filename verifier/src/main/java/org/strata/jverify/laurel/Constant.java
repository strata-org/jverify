package org.strata.jverify.laurel;

public record Constant(Identifier name, AstNode type, AstNode initializer) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", name().toIon(ion));
        s.put("type", type().toIon(ion));
        s.put("initializer", (initializer() != null ? initializer().toIon(ion) : ion.newNull()));
        return s;
    }
}
