package org.strata.jverify.laurel;

public record Procedure(Identifier name, java.util.List<Parameter> inputs, java.util.List<Parameter> outputs, java.util.List<Condition> preconditions, AstNode decreases, boolean isFunctional, Body body, AstNode invokeOn, java.util.List<AstNode> axioms) implements ToIon {
    public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var s = ion.newEmptyStruct();
        s.put("name", name().toIon(ion));
        var _l_inputs = ion.newEmptyList();
        for (var e : inputs()) _l_inputs.add(e.toIon(ion));
        s.put("inputs", _l_inputs);
        var _l_outputs = ion.newEmptyList();
        for (var e : outputs()) _l_outputs.add(e.toIon(ion));
        s.put("outputs", _l_outputs);
        var _l_preconditions = ion.newEmptyList();
        for (var e : preconditions()) _l_preconditions.add(e.toIon(ion));
        s.put("preconditions", _l_preconditions);
        s.put("decreases", (decreases() != null ? decreases().toIon(ion) : ion.newNull()));
        s.put("isFunctional", ion.newBool(isFunctional()));
        s.put("body", body().toIon(ion));
        s.put("invokeOn", (invokeOn() != null ? invokeOn().toIon(ion) : ion.newNull()));
        var _l_axioms = ion.newEmptyList();
        for (var e : axioms()) _l_axioms.add(e.toIon(ion));
        s.put("axioms", _l_axioms);
        return s;
    }
}
