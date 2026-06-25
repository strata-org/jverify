package org.strata.jverify.laurel;

public sealed interface TypeDefinition extends ToIon permits TypeDefinition.Composite, TypeDefinition.Constrained, TypeDefinition.Datatype, TypeDefinition.Alias {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Composite(CompositeType ty) implements TypeDefinition {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Composite"));
        sexp.add(ty().toIon(ion));
        return sexp;
        }
    }

    public record Constrained(ConstrainedType ty) implements TypeDefinition {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Constrained"));
        sexp.add(ty().toIon(ion));
        return sexp;
        }
    }

    public record Datatype(DatatypeDefinition ty) implements TypeDefinition {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Datatype"));
        sexp.add(ty().toIon(ion));
        return sexp;
        }
    }

    public record Alias(TypeAlias ty) implements TypeDefinition {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Alias"));
        sexp.add(ty().toIon(ion));
        return sexp;
        }
    }
}
