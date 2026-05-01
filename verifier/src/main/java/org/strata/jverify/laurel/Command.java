package org.strata.jverify.laurel;

public sealed interface Command permits Command.CompositeCommand, Command.ProcedureCommand, Command.DatatypeCommand, Command.ConstrainedTypeCommand {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record CompositeCommand(SourceRange sourceRange, Composite composite) implements Command {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("compositeCommand"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(composite.toIon(ion)); return sexp;
        }
    }

    public record ProcedureCommand(SourceRange sourceRange, Procedure procedure) implements Command {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("procedureCommand"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(procedure.toIon(ion)); return sexp;
        }
    }

    public record DatatypeCommand(SourceRange sourceRange, Datatype datatype) implements Command {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("datatypeCommand"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(datatype.toIon(ion)); return sexp;
        }
    }

    public record ConstrainedTypeCommand(SourceRange sourceRange, ConstrainedType ct) implements Command {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("constrainedTypeCommand"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ct.toIon(ion)); return sexp;
        }
    }
}
