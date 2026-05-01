package org.strata.jverify.laurel;

public record EnsuresClause(SourceRange sourceRange, StmtExpr cond, ErrorSummary errorMessage) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        s.put("cond", cond.toIon(ion));
        s.put("errorMessage", errorMessage != null ? errorMessage.toIon(ion) : ion.newNull());
        return s;
    }
}
