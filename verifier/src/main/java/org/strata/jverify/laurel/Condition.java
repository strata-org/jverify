package org.strata.jverify.laurel;

public record Condition(AstNode condition, java.lang.String summary, boolean free) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("condition", condition().toIon(ion));
        s.put("summary", (summary() != null ? ion.newString(summary()) : ion.newNull()));
        s.put("free", ion.newBool(free()));
        return s;
    }
}
