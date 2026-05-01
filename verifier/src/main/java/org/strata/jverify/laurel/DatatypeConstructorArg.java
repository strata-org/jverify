package org.strata.jverify.laurel;

public record DatatypeConstructorArg(SourceRange sourceRange, java.lang.String name, LaurelType argType) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        s.put("name", ion.newString(name));
        s.put("argType", argType.toIon(ion));
        return s;
    }
}
