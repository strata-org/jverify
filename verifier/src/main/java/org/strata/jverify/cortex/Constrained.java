package org.strata.jverify.cortex;

public record Constrained(java.lang.String name, Ty base, java.lang.String binder, Expr constraint, java.util.Optional<Expr> witness) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", ion.newString(name()));
        s.put("base", base().toIon(ion));
        s.put("binder", ion.newString(binder()));
        s.put("constraint", constraint().toIon(ion));
        s.put("witness", (witness().isPresent() ? witness().get().toIon(ion) : ion.newNull()));
        return s;
    }
}
