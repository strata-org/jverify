package com.aws.jverify.verifier.compiler;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.Label;
import com.aws.jverify.generated.Statement;
import com.sun.tools.javac.tree.JCTree;

import java.util.List;

public interface StatementSimplifier {

    @Nullable
    List<Statement> simplify(JCTree.JCStatement statement,
                             List<Label> labels);
}
