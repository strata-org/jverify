package org.strata.jverify.laurel;

public sealed interface Operation extends ToIon permits Operation.Eq, Operation.Neq, Operation.And, Operation.Or, Operation.Not, Operation.Implies, Operation.AndThen, Operation.OrElse, Operation.Neg, Operation.Add, Operation.Sub, Operation.Mul, Operation.Div, Operation.Mod, Operation.DivT, Operation.ModT, Operation.Lt, Operation.Leq, Operation.Gt, Operation.Geq, Operation.StrConcat {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Eq() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Eq"));

        return sexp;
        }
    }

    public record Neq() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Neq"));

        return sexp;
        }
    }

    public record And() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("And"));

        return sexp;
        }
    }

    public record Or() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Or"));

        return sexp;
        }
    }

    public record Not() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Not"));

        return sexp;
        }
    }

    public record Implies() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Implies"));

        return sexp;
        }
    }

    public record AndThen() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("AndThen"));

        return sexp;
        }
    }

    public record OrElse() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("OrElse"));

        return sexp;
        }
    }

    public record Neg() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Neg"));

        return sexp;
        }
    }

    public record Add() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Add"));

        return sexp;
        }
    }

    public record Sub() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Sub"));

        return sexp;
        }
    }

    public record Mul() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Mul"));

        return sexp;
        }
    }

    public record Div() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Div"));

        return sexp;
        }
    }

    public record Mod() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Mod"));

        return sexp;
        }
    }

    public record DivT() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("DivT"));

        return sexp;
        }
    }

    public record ModT() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("ModT"));

        return sexp;
        }
    }

    public record Lt() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Lt"));

        return sexp;
        }
    }

    public record Leq() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Leq"));

        return sexp;
        }
    }

    public record Gt() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Gt"));

        return sexp;
        }
    }

    public record Geq() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Geq"));

        return sexp;
        }
    }

    public record StrConcat() implements Operation {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("StrConcat"));

        return sexp;
        }
    }
}
