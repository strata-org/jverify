package org.strata.jverify.laurel;

public record Field(Identifier name, boolean isMutable, AstNode<HighType> type, java.util.Optional<AstNode<StmtExpr>> initializer) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", name().toIon(ion));
        s.put("isMutable", ion.newBool(isMutable()));
        s.put("type", type().toIon(ion));
        s.put("initializer", (initializer().isPresent() ? initializer().get().toIon(ion) : ion.newNull()));
        return s;
    }
}
