package org.strata.jverify.laurel;

public sealed interface HighType permits HighType.TVoid, HighType.TBool, HighType.TInt, HighType.TFloat64, HighType.TReal, HighType.TString, HighType.TSet, HighType.TMap, HighType.UserDefined, HighType.Applied, HighType.Pure, HighType.Intersection, HighType.TBv, HighType.TCore, HighType.Unknown, HighType.MultiValuedExpr {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record TVoid() implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("TVoid"));

        return sexp;
        }
    }

    public record TBool() implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("TBool"));

        return sexp;
        }
    }

    public record TInt() implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("TInt"));

        return sexp;
        }
    }

    public record TFloat64() implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("TFloat64"));

        return sexp;
        }
    }

    public record TReal() implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("TReal"));

        return sexp;
        }
    }

    public record TString() implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("TString"));

        return sexp;
        }
    }

    public record TSet(AstNode elementType) implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("TSet"));
        sexp.add(elementType().toIon(ion));
        return sexp;
        }
    }

    public record TMap(AstNode keyType, AstNode valueType) implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("TMap"));
        sexp.add(keyType().toIon(ion));
        sexp.add(valueType().toIon(ion));
        return sexp;
        }
    }

    public record UserDefined(Identifier name) implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("UserDefined"));
        sexp.add(name().toIon(ion));
        return sexp;
        }
    }

    public record Applied(AstNode base, java.util.List<AstNode> typeArguments) implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Applied"));
        sexp.add(base().toIon(ion));
        var _l1 = ion.newEmptyList();
        for (var e : typeArguments()) _l1.add(e.toIon(ion));
        sexp.add(_l1);
        return sexp;
        }
    }

    public record Pure(AstNode base) implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Pure"));
        sexp.add(base().toIon(ion));
        return sexp;
        }
    }

    public record Intersection(java.util.List<AstNode> types) implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Intersection"));
        var _l0 = ion.newEmptyList();
        for (var e : types()) _l0.add(e.toIon(ion));
        sexp.add(_l0);
        return sexp;
        }
    }

    public record TBv(long size) implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("TBv"));
        sexp.add(ion.newInt(size()));
        return sexp;
        }
    }

    public record TCore(java.lang.String s) implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("TCore"));
        sexp.add(ion.newString(s()));
        return sexp;
        }
    }

    public record Unknown() implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Unknown"));

        return sexp;
        }
    }

    public record MultiValuedExpr(java.util.List<AstNode> types) implements HighType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("MultiValuedExpr"));
        var _l0 = ion.newEmptyList();
        for (var e : types()) _l0.add(e.toIon(ion));
        sexp.add(_l0);
        return sexp;
        }
    }
}
