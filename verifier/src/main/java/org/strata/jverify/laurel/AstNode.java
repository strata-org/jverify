package org.strata.jverify.laurel;

public record AstNode(java.lang.Object val, FileRange source) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("val", ion.newNull());
        s.put("source", (source() != null ? source().toIon(ion) : ion.newNull()));
        return s;
    }
}
