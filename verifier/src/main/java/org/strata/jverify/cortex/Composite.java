package org.strata.jverify.cortex;

public record Composite(java.lang.String name, java.util.List<java.lang.String> extending, java.util.List<Field> fields) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", ion.newString(name()));
        var _l_extending = ion.newEmptyList();
        for (var e : extending()) _l_extending.add(ion.newString(e));
        s.put("extending", _l_extending);
        var _l_fields = ion.newEmptyList();
        for (var e : fields()) _l_fields.add(e.toIon(ion));
        s.put("fields", _l_fields);
        return s;
    }
}
