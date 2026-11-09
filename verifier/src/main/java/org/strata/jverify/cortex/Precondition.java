package org.strata.jverify.cortex;

public record Precondition(Expr cond, java.util.Optional<java.lang.String> summary) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("cond", cond().toIon(ion));
        s.put("summary", (summary().isPresent() ? ion.newString(summary().get()) : ion.newNull()));
        return s;
    }
}
