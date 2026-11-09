package org.strata.jverify.cortex;

public sealed interface Transfer extends ToIon permits Transfer.Goto, Transfer.CondGoto, Transfer.Finish {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Goto(java.lang.String target, FileRange source) implements Transfer {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("goto"));
        sexp.add(ion.newString(target()));
        sexp.add(source().toIon(ion));
        return sexp;
        }
    }

    public record CondGoto(Expr cond, java.lang.String thenTarget, java.lang.String elseTarget, FileRange source) implements Transfer {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("condGoto"));
        sexp.add(cond().toIon(ion));
        sexp.add(ion.newString(thenTarget()));
        sexp.add(ion.newString(elseTarget()));
        sexp.add(source().toIon(ion));
        return sexp;
        }
    }

    public record Finish(FileRange source) implements Transfer {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("finish"));
        sexp.add(source().toIon(ion));
        return sexp;
        }
    }
}
