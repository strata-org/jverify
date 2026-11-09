package org.strata.jverify.cortex;

public sealed interface Cmd extends ToIon permits Cmd.Init, Cmd.Set, Cmd.Assert, Cmd.Assume, Cmd.New, Cmd.ReadField, Cmd.WriteField, Cmd.InstanceOf, Cmd.Call, Cmd.VirtualCall, Cmd.Throw {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Init(java.lang.String name, Ty ty, java.util.Optional<Expr> value, FileRange source) implements Cmd {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("init"));
        sexp.add(ion.newString(name()));
        sexp.add(ty().toIon(ion));
        sexp.add((value().isPresent() ? value().get().toIon(ion) : ion.newNull()));
        sexp.add(source().toIon(ion));
        return sexp;
        }
    }

    public record Set(java.lang.String name, java.util.Optional<Expr> value, FileRange source) implements Cmd {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("set"));
        sexp.add(ion.newString(name()));
        sexp.add((value().isPresent() ? value().get().toIon(ion) : ion.newNull()));
        sexp.add(source().toIon(ion));
        return sexp;
        }
    }

    public record Assert(java.lang.String label, Expr cond, java.util.Optional<java.lang.String> summary, FileRange source) implements Cmd {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("assert"));
        sexp.add(ion.newString(label()));
        sexp.add(cond().toIon(ion));
        sexp.add((summary().isPresent() ? ion.newString(summary().get()) : ion.newNull()));
        sexp.add(source().toIon(ion));
        return sexp;
        }
    }

    public record Assume(java.lang.String label, Expr cond, FileRange source) implements Cmd {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("assume"));
        sexp.add(ion.newString(label()));
        sexp.add(cond().toIon(ion));
        sexp.add(source().toIon(ion));
        return sexp;
        }
    }

    public record New(java.lang.String lhs, java.lang.String ty, FileRange source) implements Cmd {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("new"));
        sexp.add(ion.newString(lhs()));
        sexp.add(ion.newString(ty()));
        sexp.add(source().toIon(ion));
        return sexp;
        }
    }

    public record ReadField(java.lang.String lhs, Expr obj, java.lang.String owner, java.lang.String field, FileRange source) implements Cmd {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("readField"));
        sexp.add(ion.newString(lhs()));
        sexp.add(obj().toIon(ion));
        sexp.add(ion.newString(owner()));
        sexp.add(ion.newString(field()));
        sexp.add(source().toIon(ion));
        return sexp;
        }
    }

    public record WriteField(Expr obj, java.lang.String owner, java.lang.String field, Expr rhs, FileRange source) implements Cmd {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("writeField"));
        sexp.add(obj().toIon(ion));
        sexp.add(ion.newString(owner()));
        sexp.add(ion.newString(field()));
        sexp.add(rhs().toIon(ion));
        sexp.add(source().toIon(ion));
        return sexp;
        }
    }

    public record InstanceOf(java.lang.String lhs, Expr obj, java.lang.String ty, FileRange source) implements Cmd {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("instanceOf"));
        sexp.add(ion.newString(lhs()));
        sexp.add(obj().toIon(ion));
        sexp.add(ion.newString(ty()));
        sexp.add(source().toIon(ion));
        return sexp;
        }
    }

    public record Call(java.util.List<java.lang.String> lhs, java.lang.String proc, java.util.List<Expr> args, FileRange source) implements Cmd {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("call"));
        var _l0 = ion.newEmptyList();
        for (var e : lhs()) _l0.add(ion.newString(e));
        sexp.add(_l0);
        sexp.add(ion.newString(proc()));
        var _l2 = ion.newEmptyList();
        for (var e : args()) _l2.add(e.toIon(ion));
        sexp.add(_l2);
        sexp.add(source().toIon(ion));
        return sexp;
        }
    }

    public record VirtualCall(java.util.List<java.lang.String> lhs, java.lang.String owner, java.lang.String method, java.util.List<Expr> args, FileRange source) implements Cmd {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("virtualCall"));
        var _l0 = ion.newEmptyList();
        for (var e : lhs()) _l0.add(ion.newString(e));
        sexp.add(_l0);
        sexp.add(ion.newString(owner()));
        sexp.add(ion.newString(method()));
        var _l3 = ion.newEmptyList();
        for (var e : args()) _l3.add(e.toIon(ion));
        sexp.add(_l3);
        sexp.add(source().toIon(ion));
        return sexp;
        }
    }

    public record Throw(Expr exn, java.util.Optional<java.lang.String> summary, FileRange source) implements Cmd {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("throw"));
        sexp.add(exn().toIon(ion));
        sexp.add((summary().isPresent() ? ion.newString(summary().get()) : ion.newNull()));
        sexp.add(source().toIon(ion));
        return sexp;
        }
    }
}
