package org.strata.jverify.cortex;

public record Procedure(java.lang.String name, java.util.List<Param> inputs, java.util.List<Param> outputs, java.util.Optional<java.lang.String> throws_, java.util.List<Precondition> requires, java.util.Optional<Body> body, boolean entry, FileRange source) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", ion.newString(name()));
        var _l_inputs = ion.newEmptyList();
        for (var e : inputs()) _l_inputs.add(e.toIon(ion));
        s.put("inputs", _l_inputs);
        var _l_outputs = ion.newEmptyList();
        for (var e : outputs()) _l_outputs.add(e.toIon(ion));
        s.put("outputs", _l_outputs);
        s.put("throws", (throws_().isPresent() ? ion.newString(throws_().get()) : ion.newNull()));
        var _l_requires = ion.newEmptyList();
        for (var e : requires()) _l_requires.add(e.toIon(ion));
        s.put("requires", _l_requires);
        s.put("body", (body().isPresent() ? body().get().toIon(ion) : ion.newNull()));
        s.put("entry", ion.newBool(entry()));
        s.put("source", source().toIon(ion));
        return s;
    }
}
