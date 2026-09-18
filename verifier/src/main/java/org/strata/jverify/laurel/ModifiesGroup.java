package org.strata.jverify.laurel;

public record ModifiesGroup(java.util.List<AstNode<StmtExpr>> targets, java.util.Optional<AstNode<StmtExpr>> guard, java.util.Optional<java.lang.String> summary) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        var _l_targets = ion.newEmptyList();
        for (var e : targets()) _l_targets.add(e.toIon(ion));
        s.put("targets", _l_targets);
        s.put("guard", (guard().isPresent() ? guard().get().toIon(ion) : ion.newNull()));
        s.put("summary", (summary().isPresent() ? ion.newString(summary().get()) : ion.newNull()));
        return s;
    }
}
