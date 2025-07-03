package com.aws.jverify.verifier.simplify.java;

import com.aws.jverify.generated.Label;
import com.aws.jverify.generated.Statement;
import com.aws.jverify.verifier.BlockCompiler;
import com.aws.jverify.verifier.StatementSimplifier;
import com.sun.tools.javac.tree.JCTree;

import java.util.List;

public class DoWhileCompiler implements StatementSimplifier {
    BlockCompiler blockCompiler;

    public DoWhileCompiler(BlockCompiler blockCompiler) {
        this.blockCompiler = blockCompiler;
    }

    @Override
    public List<Statement> simplify(JCTree.JCStatement statement, List<Label> labels) {
        if (statement instanceof JCTree.JCDoWhileLoop doWhileLoop) {
            var whileLoop = blockCompiler.translateLoop(doWhileLoop,
                    doWhileLoop.getCondition(),
                    doWhileLoop.body, labels, x -> x);
            var firstBlock = whileLoop.getBody();
            return List.of(firstBlock, whileLoop);
        }
        return null;
    }
}
