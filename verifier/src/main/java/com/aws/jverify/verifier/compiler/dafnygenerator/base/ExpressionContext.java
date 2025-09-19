package com.aws.jverify.verifier.compiler.dafnygenerator.base;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.Statement;
import net.bytebuddy.implementation.LoadedTypeInitializer;

import java.util.function.Consumer;

public record ExpressionContext(@Nullable Consumer<Statement> statementWriter, boolean allowImpure, @Nullable BlockCompiler blockCompiler) {
    public final static ExpressionContext Pure =  new ExpressionContext(null, false, null);
    
    public ExpressionContext forbidImpure() {
        return new ExpressionContext(statementWriter, false, blockCompiler);
    }

    public String getVariableSuffix() {
        return blockCompiler.generatedIndex++ + "";
    }
}
