package org.strata.jverify.cortex;

public sealed interface Expr extends ToIon permits Expr.Var, Expr.Int, Expr.Bool, Expr.Str, Expr.Bv, Expr.Op, Expr.Ite, Expr.Eq {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Var(java.lang.String name) implements Expr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("var"));
        sexp.add(ion.newString(name()));
        return sexp;
        }
    }

    public record Int(long value) implements Expr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("int"));
        sexp.add(ion.newInt(value()));
        return sexp;
        }
    }

    public record Bool(boolean value) implements Expr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("bool"));
        sexp.add(ion.newBool(value()));
        return sexp;
        }
    }

    public record Str(java.lang.String value) implements Expr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("str"));
        sexp.add(ion.newString(value()));
        return sexp;
        }
    }

    public record Bv(long width, long value) implements Expr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("bv"));
        sexp.add(ion.newInt(width()));
        sexp.add(ion.newInt(value()));
        return sexp;
        }
    }

    public record Op(java.lang.String name, java.util.List<Expr> args) implements Expr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("op"));
        sexp.add(ion.newString(name()));
        var _l1 = ion.newEmptyList();
        for (var e : args()) _l1.add(e.toIon(ion));
        sexp.add(_l1);
        return sexp;
        }
    }

    public record Ite(Expr cond, Expr thenExpr, Expr elseExpr) implements Expr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("ite"));
        sexp.add(cond().toIon(ion));
        sexp.add(thenExpr().toIon(ion));
        sexp.add(elseExpr().toIon(ion));
        return sexp;
        }
    }

    public record Eq(Expr lhs, Expr rhs) implements Expr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("eq"));
        sexp.add(lhs().toIon(ion));
        sexp.add(rhs().toIon(ion));
        return sexp;
        }
    }
}
