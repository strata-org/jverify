package com.aws.jverify.verifier.compiler.dafnygenerator.base;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.Expression;
import com.aws.jverify.generated.Statement;
import com.sun.tools.javac.code.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public record ExpressionContext(@Nullable Consumer<Statement> statementWriter,
                                boolean allowImpure,
                                @Nullable BlockCompiler blockCompiler,
                                @Nullable Type expectedType,
                                List<FlowCast> thenFlowCasts) {
    
    public final static ExpressionContext Pure =  new ExpressionContext(null, false, null, null, new ArrayList<>());
    
    public ExpressionContext withExpectedType(Type expectedType) {
        return new ExpressionContext(statementWriter, false, blockCompiler, expectedType, thenFlowCasts);
    }
    
    public ExpressionContext forbidImpure() {
        return new ExpressionContext(statementWriter, false, blockCompiler, expectedType, thenFlowCasts);
    }

    public String getVariableSuffix() {
        return blockCompiler.generatedIndex++ + "";
    }
}
