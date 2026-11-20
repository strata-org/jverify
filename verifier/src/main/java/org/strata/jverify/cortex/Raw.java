package org.strata.jverify.cortex;

public record Raw(long byteIdx) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("byteIdx", ion.newInt(byteIdx()));
        return s;
    }
}
