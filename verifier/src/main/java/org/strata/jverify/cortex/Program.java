package org.strata.jverify.cortex;

public record Program(java.util.List<Composite> composites, java.util.List<Constrained> constrained, java.util.List<Procedure> procedures, java.util.List<java.lang.String> preludes) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        var _l_composites = ion.newEmptyList();
        for (var e : composites()) _l_composites.add(e.toIon(ion));
        s.put("composites", _l_composites);
        var _l_constrained = ion.newEmptyList();
        for (var e : constrained()) _l_constrained.add(e.toIon(ion));
        s.put("constrained", _l_constrained);
        var _l_procedures = ion.newEmptyList();
        for (var e : procedures()) _l_procedures.add(e.toIon(ion));
        s.put("procedures", _l_procedures);
        var _l_preludes = ion.newEmptyList();
        for (var e : preludes()) _l_preludes.add(ion.newString(e));
        s.put("preludes", _l_preludes);
        return s;
    }
}
