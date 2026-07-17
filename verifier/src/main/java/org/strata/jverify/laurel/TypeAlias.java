package org.strata.jverify.laurel;

public record TypeAlias(Identifier name, AstNode<HighType> target) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", name().toIon(ion));
        s.put("target", target().toIon(ion));
        return s;
    }
}
