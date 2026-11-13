package org.strata.jverify.cortex;

public record ArrayDecl(java.lang.String name, Ty elem, java.util.List<java.lang.String> extending) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", ion.newString(name()));
        s.put("elem", elem().toIon(ion));
        var _l_extending = ion.newEmptyList();
        for (var e : extending()) _l_extending.add(ion.newString(e));
        s.put("extending", _l_extending);
        return s;
    }
}
