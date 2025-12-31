package com.aws.jverify.verifier.compiler.generator.base.dafny;

import com.aws.jverify.generated.Expression;

import java.util.List;

public record ExpressionWithFlows(Expression expression, List<FlowCast> flows) {
    public ExpressionWithFlows(Expression expression) {
        this(expression, List.of());
    }
}
