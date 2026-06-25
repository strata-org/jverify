package org.strata.jverify.laurel;

public record ConstrainedType(Identifier name, AstNode base, Identifier valueName, AstNode constraint, AstNode witness) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", name().toIon(ion));
        s.put("base", base().toIon(ion));
        s.put("valueName", valueName().toIon(ion));
        s.put("constraint", constraint().toIon(ion));
        s.put("witness", witness().toIon(ion));
        return s;
    }
}
