package org.strata.jverify.laurel;

public record Initializer(SourceRange sourceRange, StmtExpr value) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        s.put("value", value.toIon(ion));
        return s;
    }
}
