package org.strata.jverify.laurel;

public record TypeAlias(Identifier name, java.lang.Object target) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", name().toIon(ion));
        s.put("target", ion.newNull());
        return s;
    }
}
