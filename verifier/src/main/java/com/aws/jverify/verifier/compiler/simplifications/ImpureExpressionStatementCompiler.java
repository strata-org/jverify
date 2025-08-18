package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.BlockCompiler;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;

import java.util.List;

public class ImpureExpressionStatementCompiler implements StatementCompiler {
    BlockCompiler blockCompiler;

    public ImpureExpressionStatementCompiler(BlockCompiler blockCompiler) {
        this.blockCompiler = blockCompiler;
    }

    @Override
    public @Nullable List<Statement> compile(JCTree.JCStatement statement, List<Label> labels) {
        if (statement instanceof JCTree.JCExpressionStatement expressionStatement) {
            var expr = expressionStatement.getExpression();
            switch (expr) {
                case JCTree.JCAssignOp assignOp -> {
                    return translateAssignOp(assignOp);
                }
                case JCTree.JCUnary unary -> {
                    return translateUnaryExpressionStatement(unary);
                }
                default -> {}
            }
        }
        return null;
    }


    private List<Statement> translateUnaryExpressionStatement(JCTree.JCUnary unary) {
        var origin = blockCompiler.compiler.toOrigin(unary);
        var tag = unary.getTag();
        switch (tag) {
            case JCTree.Tag.POSTINC, POSTDEC, JCTree.Tag.PREINC, JCTree.Tag.PREDEC -> {
                Expression target = blockCompiler.compiler.expressionCompiler.toExpr(unary.getExpression());
                List<Expression> lhss = List.of(target);

                var opCode = (tag == JCTree.Tag.POSTINC || tag == JCTree.Tag.PREINC)
                        ? BinaryExprOpcode.Add : BinaryExprOpcode.Sub;

                Expression incrementValue;
                if (unary.type.getTag() == TypeTag.DOUBLE) {
                    incrementValue = blockCompiler.compiler.expressionCompiler.translateFp64Literal(origin, 1.0);
                } else {
                    incrementValue = new LiteralExpr(origin, 1);
                }

                var incremented = new BinaryExpr(origin, opCode, target, incrementValue);
                List<AssignmentRhs> rhss = List.of(new ExprRhs(origin, null, incremented));
                return List.of(new AssignStatement(origin, null, lhss, rhss, false));

            }
            default -> {
                throw new JavaViolationException();
            }
        }
    }

    private List<Statement> translateAssignOp(JCTree.JCAssignOp assignOp) {
        var origin = blockCompiler.compiler.toOrigin(assignOp);
        Expression target = blockCompiler.compiler.expressionCompiler.toExpr(assignOp.getVariable());
        List<Expression> lhss = List.of(target);
        var operated = blockCompiler.compiler.expressionCompiler.translateBinary(
                assignOp, assignOp.getVariable().type, null,
                assignOp.getOperator(), target, blockCompiler.compiler.expressionCompiler.toExpr(assignOp.getExpression()));
        List<AssignmentRhs> rhss = List.of(new ExprRhs(origin, null, operated));
        return List.of(new AssignStatement(origin, null, lhss, rhss, false));
    }
}
