package org.strata.jverify.laurel;

public record ErrorSummary(SourceRange sourceRange, java.lang.String msg) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        s.put("msg", ion.newString(msg));
        return s;
    }
}
