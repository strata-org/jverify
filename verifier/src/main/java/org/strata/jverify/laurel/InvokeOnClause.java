package org.strata.jverify.laurel;

public record InvokeOnClause(SourceRange sourceRange, StmtExpr trigger) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        s.put("trigger", trigger.toIon(ion));
        return s;
    }
}
