package org.strata.jverify.laurel;

public record Program(java.util.List<Procedure> staticProcedures, java.util.List<Field> staticFields, java.util.List<TypeDefinition> types, java.util.List<Constant> constants) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        var _l_staticProcedures = ion.newEmptyList();
        for (var e : staticProcedures()) _l_staticProcedures.add(e.toIon(ion));
        s.put("staticProcedures", _l_staticProcedures);
        var _l_staticFields = ion.newEmptyList();
        for (var e : staticFields()) _l_staticFields.add(e.toIon(ion));
        s.put("staticFields", _l_staticFields);
        var _l_types = ion.newEmptyList();
        for (var e : types()) _l_types.add(e.toIon(ion));
        s.put("types", _l_types);
        var _l_constants = ion.newEmptyList();
        for (var e : constants()) _l_constants.add(e.toIon(ion));
        s.put("constants", _l_constants);
        return s;
    }
}
