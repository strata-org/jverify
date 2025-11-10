package com.aws.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.tree.JCTree;

import java.util.List;

public record MethodOrLoopContract(
        List<Property<JCTree.JCExpression>> preconditions,
        List<Property<JCTree.JCExpression>> postconditions,
        List<Property<JCTree.JCExpression>> loopInvariants,
        List<JCTree.JCExpression> decreases,
        List<Property<JCTree.JCExpression>> reads,
        List<Property<JCTree.JCExpression>> modifies) {
}
