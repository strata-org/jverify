package org.strata.jverify.laurel;

public record ConstrainedType(SourceRange sourceRange, java.lang.String name, java.lang.String valueName, LaurelType base, StmtExpr constraint, StmtExpr witness) {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("sourceRange", sourceRange.toIon(ion));
        s.put("name", ion.newString(name));
        s.put("valueName", ion.newString(valueName));
        s.put("base", base.toIon(ion));
        s.put("constraint", constraint.toIon(ion));
        s.put("witness", witness.toIon(ion));
        return s;
    }
}
