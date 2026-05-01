package org.strata.jverify.laurel;

public sealed interface Body permits Body.Body_, Body.ExternalBody {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Body_(SourceRange sourceRange, StmtExpr body) implements Body {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("body"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(body.toIon(ion)); return sexp;
        }
    }

    public record ExternalBody(SourceRange sourceRange) implements Body {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("externalBody"));
            sexp.add(sourceRange.toIon(ion)); return sexp;
        }
    }
}
