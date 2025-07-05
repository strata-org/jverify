package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.Label;
import com.aws.jverify.generated.Statement;
import com.sun.tools.javac.tree.JCTree;

import java.util.List;

public interface StatementCompiler {

    @Nullable
    List<Statement> compile(JCTree.JCStatement statement, List<Label> labels);
}
