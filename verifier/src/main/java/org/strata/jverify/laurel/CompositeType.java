package org.strata.jverify.laurel;

public record CompositeType(Identifier name, java.util.List<Identifier> extending, java.util.List<Field> fields, java.util.List<Procedure> instanceProcedures) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", name().toIon(ion));
        var _l_extending = ion.newEmptyList();
        for (var e : extending()) _l_extending.add(e.toIon(ion));
        s.put("extending", _l_extending);
        var _l_fields = ion.newEmptyList();
        for (var e : fields()) _l_fields.add(e.toIon(ion));
        s.put("fields", _l_fields);
        var _l_instanceProcedures = ion.newEmptyList();
        for (var e : instanceProcedures()) _l_instanceProcedures.add(e.toIon(ion));
        s.put("instanceProcedures", _l_instanceProcedures);
        return s;
    }
}
