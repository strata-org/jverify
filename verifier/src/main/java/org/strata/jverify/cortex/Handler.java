package org.strata.jverify.cortex;

public record Handler(java.util.List<java.lang.String> covered, java.util.Optional<java.lang.String> catchType, java.lang.String binder, java.lang.String target, FileRange source) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        var _l_covered = ion.newEmptyList();
        for (var e : covered()) _l_covered.add(ion.newString(e));
        s.put("covered", _l_covered);
        s.put("catchType", (catchType().isPresent() ? ion.newString(catchType().get()) : ion.newNull()));
        s.put("binder", ion.newString(binder()));
        s.put("target", ion.newString(target()));
        s.put("source", source().toIon(ion));
        return s;
    }
}
