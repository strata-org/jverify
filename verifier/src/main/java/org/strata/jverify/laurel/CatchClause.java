package org.strata.jverify.laurel;

public record CatchClause(Identifier binding, java.util.Optional<AstNode<StmtExpr>> predicate, AstNode<StmtExpr> body, AstNode<HighType> bindingType) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("binding", binding().toIon(ion));
        s.put("predicate", (predicate().isPresent() ? predicate().get().toIon(ion) : ion.newNull()));
        s.put("body", body().toIon(ion));
        s.put("bindingType", bindingType().toIon(ion));
        return s;
    }
}
