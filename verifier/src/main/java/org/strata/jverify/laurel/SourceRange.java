package org.strata.jverify.laurel;

public record SourceRange(Raw start, Raw stop) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("start", start().toIon(ion));
        s.put("stop", stop().toIon(ion));
        return s;
    }
}
