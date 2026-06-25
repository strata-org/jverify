package org.strata.jverify.laurel;

public record FileRange(Uri file, SourceRange range) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("file", file().toIon(ion));
        s.put("range", range().toIon(ion));
        return s;
    }
}
