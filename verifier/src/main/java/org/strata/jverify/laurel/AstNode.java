package org.strata.jverify.laurel;

public record AstNode<T extends ToIon>(T val, FileRange source) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("val", val().toIon(ion));
        s.put("source", (source() != null ? source().toIon(ion) : ion.newNull()));
        return s;
    }
}
