package org.strata.jverify.laurel;

public record Datatype(SourceRange sourceRange, java.lang.String name, DatatypeConstructorList constructors) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        s.put("name", ion.newString(name));
        s.put("constructors", constructors.toIon(ion));
        return s;
    }
}
