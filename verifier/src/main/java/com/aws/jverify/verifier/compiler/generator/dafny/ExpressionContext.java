package com.aws.jverify.verifier.compiler.generator.dafny;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.Statement;
import com.sun.tools.javac.code.Type;

import java.util.function.Consumer;

public record ExpressionContext(@Nullable Consumer<Statement> statementWriter,
                                boolean allowImpure,
                                @Nullable BlockCompiler blockCompiler,
                                @Nullable Type expectedType) {

    public final static ExpressionContext Pure =  new ExpressionContext(null, false, null, null);

    public ExpressionContext withExpectedType(Type expectedType) {
        return new ExpressionContext(statementWriter, false, blockCompiler, expectedType);
    }

    public ExpressionContext forbidImpure() {
        return new ExpressionContext(statementWriter, false, blockCompiler, expectedType);
    }

    public ExpressionContext withStatementWriter() {
        return new ExpressionContext(statementWriter, false, blockCompiler, expectedType);
    }

    public String getVariableSuffix() {
        return blockCompiler.generatedIndex++ + "";
    }
}
