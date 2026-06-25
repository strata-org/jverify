package org.strata.jverify.laurel;

public record Uri(java.lang.String path) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("_0", ion.newString(path()));
        return s;
    }
}
