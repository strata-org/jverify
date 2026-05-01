package org.strata.jverify.laurel;

public record SourceRange(long start, long stop) {
    public static final SourceRange NONE = new SourceRange(0, 0);

    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("start", ion.newInt(start));
        s.put("stop", ion.newInt(stop));
        return s;
    }
}
