package org.strata.jverify.laurel;

public record Condition(AstNode<StmtExpr> condition, java.util.Optional<java.lang.String> summary, ConditionMode mode) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("condition", condition().toIon(ion));
        s.put("summary", (summary().isPresent() ? ion.newString(summary().get()) : ion.newNull()));
        s.put("mode", mode().toIon(ion));
        return s;
    }
}
