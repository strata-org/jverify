package com.aws.jverify.verifier.compiler;

import com.sun.tools.javac.tree.JCTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record LoweredResult(ArrayList<JCTree.JCCompilationUnit> parsed, Set<JCTree.JCCompilationUnit> libraries) {}
