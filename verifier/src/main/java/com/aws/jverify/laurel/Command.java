package com.aws.jverify.laurel;

public sealed interface Command extends Node permits Command.CompositeCommand, Command.ProcedureCommand, Command.DatatypeCommand, Command.ConstrainedTypeCommand {
    public record CompositeCommand(
        SourceRange sourceRange,
        Composite composite
    ) implements Command {
        @Override
        public java.lang.String operationName() { return "Laurel.compositeCommand"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.compositeCommand", sourceRange());
            sexp.add($s.serialize(composite()));
            return sexp;
        }
    }

    public record ProcedureCommand(
        SourceRange sourceRange,
        Procedure procedure
    ) implements Command {
        @Override
        public java.lang.String operationName() { return "Laurel.procedureCommand"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.procedureCommand", sourceRange());
            sexp.add($s.serialize(procedure()));
            return sexp;
        }
    }

    public record DatatypeCommand(
        SourceRange sourceRange,
        Datatype datatype
    ) implements Command {
        @Override
        public java.lang.String operationName() { return "Laurel.datatypeCommand"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.datatypeCommand", sourceRange());
            sexp.add($s.serialize(datatype()));
            return sexp;
        }
    }

    public record ConstrainedTypeCommand(
        SourceRange sourceRange,
        ConstrainedType ct
    ) implements Command {
        @Override
        public java.lang.String operationName() { return "Laurel.constrainedTypeCommand"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.constrainedTypeCommand", sourceRange());
            sexp.add($s.serialize(ct()));
            return sexp;
        }
    }
}
