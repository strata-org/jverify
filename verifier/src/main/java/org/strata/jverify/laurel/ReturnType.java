package org.strata.jverify.laurel;

public record ReturnType(SourceRange sourceRange, LaurelType returnType) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        s.put("returnType", returnType.toIon(ion));
        return s;
    }
}
