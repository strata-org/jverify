package org.strata.jverify.laurel;

public record Identifier(java.lang.String text, java.util.Optional<Long> uniqueId, FileRange source) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("text", ion.newString(text()));
        s.put("uniqueId", (uniqueId().isPresent() ? ion.newInt(uniqueId().get()) : ion.newNull()));
        s.put("source", source().toIon(ion));
        return s;
    }
}
