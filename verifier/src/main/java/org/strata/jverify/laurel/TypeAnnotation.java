package org.strata.jverify.laurel;

public record TypeAnnotation(SourceRange sourceRange, LaurelType varType) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        s.put("varType", varType.toIon(ion));
        return s;
    }
}
