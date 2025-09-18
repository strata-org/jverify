package com.aws.jverify.verifier.compiler.dafnygenerator.base;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.Statement;
import net.bytebuddy.implementation.LoadedTypeInitializer;

import java.util.function.Consumer;

public record ExpressionContext(@Nullable Consumer<Statement> statementWriter, boolean allowImpure) {
    public final static ExpressionContext Pure =  new ExpressionContext(null, false);
    
    public ExpressionContext forbidImpure() {
        return new ExpressionContext(statementWriter, false);
    }
}
