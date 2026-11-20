package org.strata.jverify.cortex;

public record Global(java.lang.String name, Ty ty, java.util.Optional<Expr> init, FileRange source) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", ion.newString(name()));
        s.put("ty", ty().toIon(ion));
        s.put("init", (init().isPresent() ? init().get().toIon(ion) : ion.newNull()));
        s.put("source", source().toIon(ion));
        return s;
    }
}
