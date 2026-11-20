package org.strata.jverify.cortex;

public record Body(java.lang.String entry, java.util.List<Block> blocks, java.util.List<Handler> handlers) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("entry", ion.newString(entry()));
        var _l_blocks = ion.newEmptyList();
        for (var e : blocks()) _l_blocks.add(e.toIon(ion));
        s.put("blocks", _l_blocks);
        var _l_handlers = ion.newEmptyList();
        for (var e : handlers()) _l_handlers.add(e.toIon(ion));
        s.put("handlers", _l_handlers);
        return s;
    }
}
