package com.aws.jverify.verifier.compiler.dafnygenerator.base;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.Statement;
import com.aws.jverify.verifier.compiler.dafnygenerator.NullableGenerator;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;

import java.util.function.Consumer;

enum Nullability { Nullable, NonNullable }

public record ExpressionContext(@Nullable Consumer<Statement> statementWriter,
                                boolean allowImpure,
                                @Nullable BlockCompiler blockCompiler,
                                @Nullable Type fallbackType,
                                Nullability nullability) {
    public final static ExpressionContext Pure =  new ExpressionContext(null, false, null, null, Nullability.NonNullable);

    public ExpressionContext withNullability(Symbol.VarSymbol varSymbol) {
        var nullable = NullableGenerator.isNullable(varSymbol.type, varSymbol.getModifiers());
        return new ExpressionContext(statementWriter, false, blockCompiler, fallbackType, nullability);
        varSymbol.getModifiers()
    }
    
    public ExpressionContext withFallbackType(Type fallbackType) {
        return new ExpressionContext(statementWriter, false, blockCompiler, fallbackType, nullability);
    }
    
    public ExpressionContext forbidImpure() {
        return new ExpressionContext(statementWriter, false, blockCompiler, fallbackType, nullability);
    }

    public String getVariableSuffix() {
        return blockCompiler.generatedIndex++ + "";
    }
}
