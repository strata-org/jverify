package org.strata.jverify.laurel;

public record Procedure(Identifier name, java.util.List<Parameter> inputs, java.util.List<Parameter> outputs, java.util.List<Condition> preconditions, java.util.Optional<AstNode<StmtExpr>> decreases, Body body, java.util.Optional<AstNode<StmtExpr>> invokeOn, boolean isInterpretEntry, java.util.List<AstNode<StmtExpr>> axioms, java.util.Optional<AstNode<HighType>> throwsType, java.util.Optional<Identifier> throwsBinding, java.util.List<ThrowsOnBlock> throwsOn) implements ToIon {
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
        s.put("decreases", (decreases().isPresent() ? decreases().get().toIon(ion) : ion.newNull()));
        s.put("body", body().toIon(ion));
        s.put("invokeOn", (invokeOn().isPresent() ? invokeOn().get().toIon(ion) : ion.newNull()));
        s.put("isInterpretEntry", ion.newBool(isInterpretEntry()));
        var _l_axioms = ion.newEmptyList();
        for (var e : axioms()) _l_axioms.add(e.toIon(ion));
        s.put("axioms", _l_axioms);
        s.put("throwsType", (throwsType().isPresent() ? throwsType().get().toIon(ion) : ion.newNull()));
        s.put("throwsBinding", (throwsBinding().isPresent() ? throwsBinding().get().toIon(ion) : ion.newNull()));
        var _l_throwsOn = ion.newEmptyList();
        for (var e : throwsOn()) _l_throwsOn.add(e.toIon(ion));
        s.put("throwsOn", _l_throwsOn);
        return s;
    }
}
