package org.strata.jverify.laurel;

public record ConstrainedType(Identifier name, java.lang.Object base, Identifier valueName, java.lang.Object constraint, java.lang.Object witness) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", name().toIon(ion));
        s.put("base", ion.newNull());
        s.put("valueName", valueName().toIon(ion));
        s.put("constraint", ion.newNull());
        s.put("witness", ion.newNull());
        return s;
    }
}
