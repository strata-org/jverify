package com.aws.jverify.laurel;

public sealed interface LaurelType extends Node permits LaurelType.IntType, LaurelType.BoolType, LaurelType.RealType, LaurelType.Float64Type, LaurelType.StringType, LaurelType.BvType, LaurelType.CoreType, LaurelType.MapType, LaurelType.CompositeType {
    public record IntType(
        SourceRange sourceRange
    ) implements LaurelType {
        @Override
        public java.lang.String operationName() { return "Laurel.intType"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.intType", sourceRange());
            return sexp;
        }
    }

    public record BoolType(
        SourceRange sourceRange
    ) implements LaurelType {
        @Override
        public java.lang.String operationName() { return "Laurel.boolType"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.boolType", sourceRange());
            return sexp;
        }
    }

    public record RealType(
        SourceRange sourceRange
    ) implements LaurelType {
        @Override
        public java.lang.String operationName() { return "Laurel.realType"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.realType", sourceRange());
            return sexp;
        }
    }

    public record Float64Type(
        SourceRange sourceRange
    ) implements LaurelType {
        @Override
        public java.lang.String operationName() { return "Laurel.float64Type"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.float64Type", sourceRange());
            return sexp;
        }
    }

    public record StringType(
        SourceRange sourceRange
    ) implements LaurelType {
        @Override
        public java.lang.String operationName() { return "Laurel.stringType"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.stringType", sourceRange());
            return sexp;
        }
    }

    public record BvType(
        SourceRange sourceRange,
        java.math.BigInteger width
    ) implements LaurelType {
        @Override
        public java.lang.String operationName() { return "Laurel.bvType"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.bvType", sourceRange());
            sexp.add($s.serializeNum(width()));
            return sexp;
        }
    }

    public record CoreType(
        SourceRange sourceRange,
        java.lang.String name
    ) implements LaurelType {
        @Override
        public java.lang.String operationName() { return "Laurel.coreType"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.coreType", sourceRange());
            sexp.add($s.serializeIdent(name()));
            return sexp;
        }
    }

    public record MapType(
        SourceRange sourceRange,
        LaurelType keyType, LaurelType valueType
    ) implements LaurelType {
        @Override
        public java.lang.String operationName() { return "Laurel.mapType"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.mapType", sourceRange());
            sexp.add($s.serialize(keyType()));
            sexp.add($s.serialize(valueType()));
            return sexp;
        }
    }

    public record CompositeType(
        SourceRange sourceRange,
        java.lang.String name
    ) implements LaurelType {
        @Override
        public java.lang.String operationName() { return "Laurel.compositeType"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.compositeType", sourceRange());
            sexp.add($s.serializeIdent(name()));
            return sexp;
        }
    }
}
