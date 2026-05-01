package org.strata.jverify.laurel;

public sealed interface LaurelType permits LaurelType.IntType, LaurelType.BoolType, LaurelType.RealType, LaurelType.Float64Type, LaurelType.StringType, LaurelType.BvType, LaurelType.CoreType, LaurelType.MapType, LaurelType.CompositeType {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record IntType(SourceRange sourceRange) implements LaurelType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp();
            sexp.add(ion.newSymbol("intType"));
            sexp.add(sourceRange.toIon(ion));
            return sexp;
        }
    }

    public record BoolType(SourceRange sourceRange) implements LaurelType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp();
            sexp.add(ion.newSymbol("boolType"));
            sexp.add(sourceRange.toIon(ion));
            return sexp;
        }
    }

    public record RealType(SourceRange sourceRange) implements LaurelType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp();
            sexp.add(ion.newSymbol("realType"));
            sexp.add(sourceRange.toIon(ion));
            return sexp;
        }
    }

    public record Float64Type(SourceRange sourceRange) implements LaurelType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp();
            sexp.add(ion.newSymbol("float64Type"));
            sexp.add(sourceRange.toIon(ion));
            return sexp;
        }
    }

    public record StringType(SourceRange sourceRange) implements LaurelType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp();
            sexp.add(ion.newSymbol("stringType"));
            sexp.add(sourceRange.toIon(ion));
            return sexp;
        }
    }

    public record BvType(SourceRange sourceRange, long width) implements LaurelType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp();
            sexp.add(ion.newSymbol("bvType"));
            sexp.add(sourceRange.toIon(ion));
            sexp.add(ion.newInt(width));
            return sexp;
        }
    }

    public record CoreType(SourceRange sourceRange, java.lang.String name) implements LaurelType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp();
            sexp.add(ion.newSymbol("coreType"));
            sexp.add(sourceRange.toIon(ion));
            sexp.add(ion.newString(name));
            return sexp;
        }
    }

    public record MapType(SourceRange sourceRange, LaurelType keyType, LaurelType valueType) implements LaurelType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp();
            sexp.add(ion.newSymbol("mapType"));
            sexp.add(sourceRange.toIon(ion));
            sexp.add(keyType.toIon(ion));
            sexp.add(valueType.toIon(ion));
            return sexp;
        }
    }

    public record CompositeType(SourceRange sourceRange, java.lang.String name) implements LaurelType {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp();
            sexp.add(ion.newSymbol("compositeType"));
            sexp.add(sourceRange.toIon(ion));
            sexp.add(ion.newString(name));
            return sexp;
        }
    }
}
