package org.strata.jverify.laurel;

public record Identifier(java.lang.String text, Long uniqueId, FileRange source) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("text", ion.newString(text()));
        s.put("uniqueId", (uniqueId() != null ? ion.newInt(uniqueId()) : ion.newNull()));
        s.put("source", (source() != null ? source().toIon(ion) : ion.newNull()));
        return s;
    }
}
