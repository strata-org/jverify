package org.strata.jverify.laurel;

public record ReturnParameters(SourceRange sourceRange, java.util.List<Parameter> parameters) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        var _l = ion.newEmptyList(); for (var e : parameters) _l.add(e.toIon(ion));
        s.put("parameters", _l);
        return s;
    }
}
