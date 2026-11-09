package org.strata.jverify.cortex;

public record Block(java.lang.String label, java.util.List<Cmd> cmds, Transfer transfer) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("label", ion.newString(label()));
        var _l_cmds = ion.newEmptyList();
        for (var e : cmds()) _l_cmds.add(e.toIon(ion));
        s.put("cmds", _l_cmds);
        s.put("transfer", transfer().toIon(ion));
        return s;
    }
}
