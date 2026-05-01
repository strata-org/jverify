package org.strata.jverify.laurel;

public record Parameter(SourceRange sourceRange, java.lang.String name, LaurelType paramType) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        s.put("name", ion.newString(name));
        s.put("paramType", paramType.toIon(ion));
        return s;
    }
}
