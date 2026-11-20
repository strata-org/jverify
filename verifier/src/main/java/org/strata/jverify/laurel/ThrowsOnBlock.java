package org.strata.jverify.laurel;

public record ThrowsOnBlock(AstNode<StmtExpr> guard, java.util.List<Condition> postconditions, java.util.List<AstNode<StmtExpr>> modifies) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("guard", guard().toIon(ion));
        var _l_postconditions = ion.newEmptyList();
        for (var e : postconditions()) _l_postconditions.add(e.toIon(ion));
        s.put("postconditions", _l_postconditions);
        var _l_modifies = ion.newEmptyList();
        for (var e : modifies()) _l_modifies.add(e.toIon(ion));
        s.put("modifies", _l_modifies);
        return s;
    }
}
