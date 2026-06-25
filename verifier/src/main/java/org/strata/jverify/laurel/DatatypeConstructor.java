package org.strata.jverify.laurel;

public record DatatypeConstructor(Identifier name, java.util.List<Parameter> args, Identifier testerName) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", name().toIon(ion));
        var _l_args = ion.newEmptyList();
        for (var e : args()) _l_args.add(e.toIon(ion));
        s.put("args", _l_args);
        s.put("testerName", testerName().toIon(ion));
        return s;
    }
}
