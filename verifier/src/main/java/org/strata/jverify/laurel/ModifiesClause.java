package org.strata.jverify.laurel;

public record ModifiesClause(SourceRange sourceRange, java.util.List<StmtExpr> refs) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        var _l = ion.newEmptyList(); for (var e : refs) _l.add(e.toIon(ion));
        s.put("refs", _l);
        return s;
    }
}
