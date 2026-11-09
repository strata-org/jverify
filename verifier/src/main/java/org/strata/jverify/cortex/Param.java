package org.strata.jverify.cortex;

public record Param(java.lang.String name, Ty ty) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", ion.newString(name()));
        s.put("ty", ty().toIon(ion));
        return s;
    }
}
