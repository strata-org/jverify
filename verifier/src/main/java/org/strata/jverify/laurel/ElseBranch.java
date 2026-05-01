package org.strata.jverify.laurel;

public record ElseBranch(SourceRange sourceRange, StmtExpr stmts) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        s.put("stmts", stmts.toIon(ion));
        return s;
    }
}
