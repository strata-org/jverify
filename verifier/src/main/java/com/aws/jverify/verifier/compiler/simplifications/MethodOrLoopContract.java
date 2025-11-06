package com.aws.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.tree.JCTree;

import java.util.List;

public record MethodOrLoopContract(
        Property<JCTree.JCExpression> precondition,
        Property<JCTree.JCExpression> postcondition,
        Property<JCTree.JCExpression> loopInvariant,
        List<JCTree.JCExpression> decreases,
        Property<JCTree.JCExpression> reads,
        Property<JCTree.JCExpression> modifies) {
}
