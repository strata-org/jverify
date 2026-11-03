package org.strata.jverify.laurel;

public sealed interface Variable extends ToIon permits Variable.Local, Variable.Field, Variable.Declare, Variable.Stack {
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

    public record Declare(ParameterOpt parameter) implements Variable {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Declare"));
        sexp.add(parameter().toIon(ion));
        return sexp;
        }
    }

    public record Stack(java.util.Optional<AstNode<HighType>> type) implements Variable {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Stack"));
        sexp.add((type().isPresent() ? type().get().toIon(ion) : ion.newNull()));
        return sexp;
        }
    }
}
