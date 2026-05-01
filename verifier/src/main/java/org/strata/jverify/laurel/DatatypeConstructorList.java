package org.strata.jverify.laurel;

public record DatatypeConstructorList(SourceRange sourceRange, java.util.List<DatatypeConstructor> constructors) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        var _l = ion.newEmptyList(); for (var e : constructors) _l.add(e.toIon(ion));
        s.put("constructors", _l);
        return s;
    }
}
