package org.strata.jverify.laurel;

public sealed interface Variable extends ToIon permits Variable.Local, Variable.Field, Variable.Declare {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Local(Identifier name) implements Variable {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Local"));
        sexp.add(name().toIon(ion));
        return sexp;
        }
    }

    public record Field(AstNode<StmtExpr> target, Identifier fieldName) implements Variable {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Field"));
        sexp.add(target().toIon(ion));
        sexp.add(fieldName().toIon(ion));
        return sexp;
        }
    }

    public record Declare(Parameter parameter) implements Variable {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Declare"));
        sexp.add(parameter().toIon(ion));
        return sexp;
        }
    }
}
