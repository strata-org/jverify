package com.aws.jverify.verifier.compiler.dafnygenerator;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BlockCompiler;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.ExpressionContext;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;

import java.util.List;

public class ImpureExpressionStatementCompiler implements StatementCompiler {
    BlockCompiler blockCompiler;

    public ImpureExpressionStatementCompiler(BlockCompiler blockCompiler) {
        this.blockCompiler = blockCompiler;
    }

    @Override
    public @Nullable List<Statement> compile(JCTree.JCStatement statement, List<Label> labels, ExpressionContext context) {
        context = context.forbidImpure();
        if (statement instanceof JCTree.JCExpressionStatement expressionStatement) {
            var expr = expressionStatement.getExpression();
            switch (expr) {
                case JCTree.JCAssignOp assignOp -> {
                    return translateAssignOp(assignOp, context);
                }
                case JCTree.JCUnary unary -> {
                    return translateUnaryExpressionStatement(unary, context);
                }
                default -> {}
            }
        }
        return null;
    }


    private List<Statement> translateUnaryExpressionStatement(JCTree.JCUnary unary, ExpressionContext context) {
        var origin = blockCompiler.generator.toOrigin(unary);
        var tag = unary.getTag();
        switch (tag) {
            case JCTree.Tag.POSTINC, POSTDEC, JCTree.Tag.PREINC, JCTree.Tag.PREDEC -> {
                if (unary.type.getTag() == TypeTag.FLOAT || unary.type.getTag() == TypeTag.DOUBLE) {
                    blockCompiler.generator.reportError(unary, "notSupported", "operator " + unary.getOperator());
                    return List.of();
                } else {
                    Expression target = blockCompiler.generator.expressionCompiler.toExpr(unary.getExpression(), context);
                    List<Expression> lhss = List.of(target);

                    var opCode = (tag == JCTree.Tag.POSTINC || tag == JCTree.Tag.PREINC)
                            ? BinaryExprOpcode.Add : BinaryExprOpcode.Sub;
                    var incremented = new BinaryExpr(origin, opCode, target, new LiteralExpr(origin, 1));
                    List<AssignmentRhs> rhss = List.of(new ExprRhs(origin, null, incremented));
                    return List.of(new AssignStatement(origin, null, lhss, rhss, false));
                }

            }
            default -> {
                // non-mutating unary expressions are not valid Java statements
                throw new JavaViolationException();
            }
        }
    }

    private List<Statement> translateAssignOp(JCTree.JCAssignOp assignOp, ExpressionContext context) {
        var origin = blockCompiler.generator.toOrigin(assignOp);
        Expression target = blockCompiler.generator.expressionCompiler.toExpr(assignOp.getVariable(), context);
        List<Expression> lhss = List.of(target);
        var operated = blockCompiler.generator.expressionCompiler.translateBinary(
                assignOp, assignOp.getVariable().type, null,
                assignOp.getOperator(), target, blockCompiler.generator.expressionCompiler.toExpr(assignOp.getExpression(), context));
        List<AssignmentRhs> rhss = List.of(new ExprRhs(origin, null, operated));
        return List.of(new AssignStatement(origin, null, lhss, rhss, false));
    }
}
