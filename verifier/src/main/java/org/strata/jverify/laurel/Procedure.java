package org.strata.jverify.laurel;

public sealed interface Procedure permits Procedure.Procedure_, Procedure.Function {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record Procedure_(SourceRange sourceRange, java.lang.String name, java.util.List<Parameter> parameters, ReturnType returnType, ReturnParameters returnParameters, java.util.List<RequiresClause> requires, InvokeOnClause invokeOn, java.util.List<EnsuresClause> ensures, java.util.List<ModifiesClause> modifies, Body body) implements Procedure {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("procedure"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newString(name));
            var _lp = ion.newEmptyList(); for (var e : parameters) _lp.add(e.toIon(ion)); sexp.add(_lp);
            sexp.add(returnType != null ? returnType.toIon(ion) : ion.newNull());
            sexp.add(returnParameters != null ? returnParameters.toIon(ion) : ion.newNull());
            var _lr = ion.newEmptyList(); for (var e : requires) _lr.add(e.toIon(ion)); sexp.add(_lr);
            sexp.add(invokeOn != null ? invokeOn.toIon(ion) : ion.newNull());
            var _le = ion.newEmptyList(); for (var e : ensures) _le.add(e.toIon(ion)); sexp.add(_le);
            var _lm = ion.newEmptyList(); for (var e : modifies) _lm.add(e.toIon(ion)); sexp.add(_lm);
            sexp.add(body != null ? body.toIon(ion) : ion.newNull());
            return sexp;
        }
    }

    public record Function(SourceRange sourceRange, java.lang.String name, java.util.List<Parameter> parameters, ReturnType returnType, ReturnParameters returnParameters, java.util.List<RequiresClause> requires, InvokeOnClause invokeOn, java.util.List<EnsuresClause> ensures, java.util.List<ModifiesClause> modifies, Body body) implements Procedure {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("function"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newString(name));
            var _lp = ion.newEmptyList(); for (var e : parameters) _lp.add(e.toIon(ion)); sexp.add(_lp);
            sexp.add(returnType != null ? returnType.toIon(ion) : ion.newNull());
            sexp.add(returnParameters != null ? returnParameters.toIon(ion) : ion.newNull());
            var _lr = ion.newEmptyList(); for (var e : requires) _lr.add(e.toIon(ion)); sexp.add(_lr);
            sexp.add(invokeOn != null ? invokeOn.toIon(ion) : ion.newNull());
            var _le = ion.newEmptyList(); for (var e : ensures) _le.add(e.toIon(ion)); sexp.add(_le);
            var _lm = ion.newEmptyList(); for (var e : modifies) _lm.add(e.toIon(ion)); sexp.add(_lm);
            sexp.add(body != null ? body.toIon(ion) : ion.newNull());
            return sexp;
        }
    }
}
