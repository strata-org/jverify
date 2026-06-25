package org.strata.jverify.laurel;

public record Constant(Identifier name, java.lang.Object type, java.lang.Object initializer) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", name().toIon(ion));
        s.put("type", ion.newNull());
        s.put("initializer", (initializer() != null ? ion.newNull() : ion.newNull()));
        return s;
    }
}
