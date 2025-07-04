package com.aws.jverify.verifier.compiler.desugar.java;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.BlockStmt;
import com.aws.jverify.generated.BreakOrContinueStmt;
import com.aws.jverify.generated.Label;
import com.aws.jverify.generated.Statement;
import com.aws.jverify.verifier.compiler.BlockCompiler;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.aws.jverify.verifier.compiler.StatementCompiler;
import com.sun.tools.javac.tree.JCTree;

import java.util.*;

public class DesugarForLoop implements StatementCompiler {

    BlockCompiler blockCompiler;
    private final Set<JCTree.JCStatement> forLoopsWithContinue = new HashSet<>();
    private final Map<JCTree.JCStatement, String> forLoopContinueLabels = new HashMap<>();

    public DesugarForLoop(BlockCompiler blockCompiler) {
        this.blockCompiler = blockCompiler;
    }

    @Override
    public @Nullable List<Statement> compile(JCTree.JCStatement statement, List<Label> labels) {
        if (statement instanceof JCTree.JCContinue jcContinue) {
            return translateContinue(jcContinue);
        } else if (statement instanceof JCTree.JCForLoop jcForLoop) {
            return translateForLoop(jcForLoop, labels);
        }
        return null;
    }
    
    private List<Statement> translateForLoop(JCTree.JCForLoop forLoop, List<Label> labels) {
        var origin = blockCompiler.compiler.toOrigin(forLoop);
        var loop = blockCompiler.translateLoop(forLoop, forLoop.getCondition(), forLoop.body, labels, bodyStatements -> {
            List<Statement> outerBody;
            List<Statement> steps = blockCompiler.translateStatements(forLoop.step);
            if (forLoopsWithContinue.contains(forLoop)) {
                var continueLabel = getForLoopContinueLabel(forLoop);
                var wrappedBody = new BlockStmt(origin, null, List.of(new Label(origin, continueLabel)), bodyStatements);
                outerBody = new ArrayList<>(1 + steps.size());
                outerBody.add(wrappedBody);
                outerBody.addAll(steps);
            } else {
                outerBody = new ArrayList<>(bodyStatements.size() + steps.size());
                outerBody.addAll(bodyStatements);
                outerBody.addAll(steps);
            }
            return outerBody;
        });

        var initializer = blockCompiler.translateStatements(forLoop.getInitializer());
        var result = new ArrayList<Statement>(initializer.size() + 1);
        result.addAll(initializer);
        result.add(loop);
        // Pack the statements in a block to ensure the loop index variable is
        // scoped
        return List.of(new BlockStmt(origin, null, List.of(), result));
    }

    @Nullable
    private List<Statement> translateContinue(JCTree.JCContinue jcContinue) {
        if (jcContinue.label == null) {
            if (blockCompiler.outerLoop == null) {
                throw new JavaViolationException();
            } else {
                if (blockCompiler.outerLoop instanceof JCTree.JCForLoop forLoop) {
                    return translateContinueForForLoop(jcContinue, forLoop);
                } else {
                    return blockCompiler.translateContinue(jcContinue);
                }
            }
        } else {
            var loop = blockCompiler.labelToLoop.get(jcContinue.label.toString());
            if (loop instanceof JCTree.JCForLoop forLoop) {
                return translateContinueForForLoop(jcContinue, forLoop);
            } else {
                return blockCompiler.translateContinue(jcContinue);
            }
        }
    }

    /**
     * For for-loops, the continue statement must jump to before the increment, instead of to before the guard 
     */
    private List<Statement> translateContinueForForLoop(JCTree.JCContinue jcContinue,
                                                        JCTree.JCForLoop forLoop) {
        var origin = blockCompiler.compiler.toOrigin(jcContinue);
        forLoopsWithContinue.add(forLoop);
        var label = getForLoopContinueLabel(forLoop);
        return List.of(new BreakOrContinueStmt(origin, null, blockCompiler.compiler.getName(jcContinue, label),
                0, false));
    }

    private String getForLoopContinueLabel(JCTree.JCForLoop forLoop) {
        return forLoopContinueLabels.computeIfAbsent(forLoop, _ -> "$loop" + blockCompiler.generatedIndex++);
    }
}
