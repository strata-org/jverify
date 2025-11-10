package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Nullable;
import com.sun.tools.javac.tree.JCTree;

import java.util.List;

public record MethodOrLoopContract(
        Property<JCTree.@Nullable JCExpression> precondition,
        Property<JCTree.@Nullable JCExpression> postcondition,
        Property<JCTree.@Nullable JCExpression> loopInvariant,
        List<JCTree.JCExpression> decreases,
        Property<JCTree.@Nullable JCExpression> reads,
        Property<JCTree.@Nullable JCExpression> modifies) {
}
