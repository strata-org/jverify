package org.strata.jverify.laurel;

public record Extends(SourceRange sourceRange, java.util.List<java.lang.String> parents) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        var _l = ion.newEmptyList(); for (var e : parents) _l.add(ion.newString(e));
        s.put("parents", _l);
        return s;
    }
}
