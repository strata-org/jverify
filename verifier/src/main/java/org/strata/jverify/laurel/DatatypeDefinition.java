package org.strata.jverify.laurel;

public record DatatypeDefinition(Identifier name, java.util.List<Identifier> typeArgs, java.util.List<DatatypeConstructor> constructors) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", name().toIon(ion));
        var _l_typeArgs = ion.newEmptyList();
        for (var e : typeArgs()) _l_typeArgs.add(e.toIon(ion));
        s.put("typeArgs", _l_typeArgs);
        var _l_constructors = ion.newEmptyList();
        for (var e : constructors()) _l_constructors.add(e.toIon(ion));
        s.put("constructors", _l_constructors);
        return s;
    }
}
