package org.strata.jverify.laurel;

public record InvariantClause(SourceRange sourceRange, StmtExpr cond) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        s.put("cond", cond.toIon(ion));
        return s;
    }
}
