package com.aws.jverify.verifier.compiler.dafnygenerator;

import com.aws.jverify.generated.Label;
import com.aws.jverify.generated.Statement;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BlockCompiler;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.ExpressionContext;
import com.sun.tools.javac.tree.JCTree;

import java.util.List;

public class DoWhileLoopCompiler implements StatementCompiler {
    BlockCompiler blockCompiler;

    public DoWhileLoopCompiler(BlockCompiler blockCompiler) {
        this.blockCompiler = blockCompiler;
    }

    @Override
    public List<Statement> compile(JCTree.JCStatement statement, List<Label> labels, ExpressionContext context) {
        if (statement instanceof JCTree.JCDoWhileLoop doWhileLoop) {
            var whileLoop = blockCompiler.translateLoop(doWhileLoop,
                    doWhileLoop.getCondition(),
                    doWhileLoop.body, labels, x -> x, context);
            var firstBlock = whileLoop.getBody();
            return List.of(firstBlock, whileLoop);
        }
        return null;
    }
}
