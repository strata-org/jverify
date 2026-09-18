package org.strata.jverify.laurel;

public record TypeAlias(Identifier name, java.util.List<Identifier> typeArgs, AstNode<HighType> target) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", name().toIon(ion));
        var _l_typeArgs = ion.newEmptyList();
        for (var e : typeArgs()) _l_typeArgs.add(e.toIon(ion));
        s.put("typeArgs", _l_typeArgs);
        s.put("target", target().toIon(ion));
        return s;
    }
}
