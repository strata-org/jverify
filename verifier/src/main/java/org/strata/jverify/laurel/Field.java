package org.strata.jverify.laurel;

public record Field(Identifier name, boolean isMutable, AstNode<HighType> type) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", name().toIon(ion));
        s.put("isMutable", ion.newBool(isMutable()));
        s.put("type", type().toIon(ion));
        return s;
    }
}
