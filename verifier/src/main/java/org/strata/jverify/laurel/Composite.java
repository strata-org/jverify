package org.strata.jverify.laurel;

public record Composite(SourceRange sourceRange, java.lang.String name, Extends extending, java.util.List<Field> fields, java.util.List<Procedure> procedures) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        s.put("name", ion.newString(name));
        s.put("extending", extending != null ? extending.toIon(ion) : ion.newNull());
        var _lf = ion.newEmptyList(); for (var e : fields) _lf.add(e.toIon(ion));
        s.put("fields", _lf);
        var _lp = ion.newEmptyList(); for (var e : procedures) _lp.add(e.toIon(ion));
        s.put("procedures", _lp);
        return s;
    }
}
