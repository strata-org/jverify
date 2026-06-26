package org.strata.jverify.laurel;

public record Parameter(Identifier name, AstNode<HighType> type) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", name().toIon(ion));
        s.put("type", type().toIon(ion));
        return s;
    }
}
