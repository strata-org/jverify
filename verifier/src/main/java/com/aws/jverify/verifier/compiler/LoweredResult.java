package com.aws.jverify.verifier.compiler;

import com.sun.tools.javac.tree.JCTree;

import java.util.List;
import java.util.Set;

public record LoweredResult(List<JCTree.JCCompilationUnit> parsed, Set<JCTree.JCCompilationUnit> libraries) {}
