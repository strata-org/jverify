package org.strata.jverify.laurel;

public sealed interface Body extends ToIon permits Body.Transparent, Body.Opaque, Body.Abstract, Body.External {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Transparent(AstNode body) implements Body {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Transparent"));
        sexp.add(body().toIon(ion));
        return sexp;
        }
    }

    public record Opaque(java.util.List<Condition> postconditions, AstNode implementation, java.util.List<AstNode> modifies) implements Body {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Opaque"));
        var _l0 = ion.newEmptyList();
        for (var e : postconditions()) _l0.add(e.toIon(ion));
        sexp.add(_l0);
        sexp.add((implementation() != null ? implementation().toIon(ion) : ion.newNull()));
        var _l2 = ion.newEmptyList();
        for (var e : modifies()) _l2.add(e.toIon(ion));
        sexp.add(_l2);
        return sexp;
        }
    }

    public record Abstract(java.util.List<Condition> postconditions) implements Body {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Abstract"));
        var _l0 = ion.newEmptyList();
        for (var e : postconditions()) _l0.add(e.toIon(ion));
        sexp.add(_l0);
        return sexp;
        }
    }

    public record External() implements Body {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("External"));

        return sexp;
        }
    }
}
