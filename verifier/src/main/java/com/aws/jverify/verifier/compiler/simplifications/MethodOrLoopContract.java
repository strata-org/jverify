package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Nullable;
import com.sun.tools.javac.tree.JCTree;

import java.util.List;

public record MethodOrLoopContract(
        List<Property<JCTree.JCExpression>> precondition,
        List<Property<JCTree.JCExpression>> postcondition,
        List<Property<JCTree.JCExpression>> loopInvariant,
        List<JCTree.JCExpression> decreases,
        List<Property<JCTree.JCExpression>> reads,
        List<Property<JCTree.JCExpression>> modifies) {
}
