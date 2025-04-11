package com.aws.jverify.verifier;

import com.aws.jverify.generated.*;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.*;

public class StatementCompiler {

    JavaToDafnyCompiler compiler;

    public StatementCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
    }

    Queue<Label> labels = new LinkedList<>();
    JCTree.JCStatement outerLoop;
    Map<String, JCTree.JCStatement> labelToLoop = new HashMap<>();
    Map<JCTree.JCStatement, String> forLoopContinueLabels = new HashMap<>();
    Set<JCTree.JCStatement> loopsWithContinue = new HashSet<>();
    
    @Nullable
    public Statement translateStatement(JCTree.JCStatement statement) {
        var origin = compiler.toOrigin(statement);
        if (statement instanceof JCTree.JCLabeledStatement labeledStatement) {
            labels.add(new Label(origin, labeledStatement.getLabel().toString()));
            return translateStatement(labeledStatement.getStatement());
        }
        var labels = this.labels.stream().toList();
        this.labels.clear();

        switch (statement) {
            case JCTree.JCExpressionStatement expressionStatement -> {
                return translateExpressionStatement(expressionStatement);
            }
            case JCTree.JCAssert assertStmt -> {
                return new AssertStmt(origin, null,
                        compiler.toExpr(assertStmt.getCondition()), null);
            }
            case JCTree.JCIf ifStatement -> {
                var condition = compiler.toExpr(ifStatement.getCondition());
                var thenBranch = JavaToDafnyCompiler.blockifyStatement(translateStatement(ifStatement.getThenStatement()));
                BlockStmt elseBranch = null;
                if (ifStatement.getElseStatement() != null) {
                    elseBranch = JavaToDafnyCompiler.blockifyStatement(translateStatement(ifStatement.getElseStatement()));
                }
                return new IfStmt(origin, null, false, condition,
                        thenBranch, elseBranch);
            }
            case JCTree.JCBlock blockStatement -> {
                return new BlockStmt(origin, null, List.of(),
                        blockStatement.getStatements().map(this::translateStatement).stream().toList());
            }
            case JCTree.JCReturn returnStatement -> {
                var expr = returnStatement.getExpression();
                if (expr == null) {
                    return new ReturnStmt(origin, null, null);
                } else {
                    return new ReturnStmt(origin, null,
                            List.of(new ExprRhs(compiler.toOrigin(expr), null, compiler.toExpr(expr))));
                }
            }
            case JCTree.JCVariableDecl variableDecl -> {
                LocalVariable localVariable = new LocalVariable(origin,
                        variableDecl.getName().toString(), compiler.toType(variableDecl.getType(), false, origin), false);
                ConcreteAssignStatement initializer = null;
                if (variableDecl.getInitializer() != null) {
                    var rhs = compiler.toAssignmentRhs(variableDecl.getInitializer());
                    List<Expression> lhss = List.of(new IdentifierExpr(localVariable.getOrigin(), localVariable.getName()));
                    List<AssignmentRhs> rhss = List.of(rhs);
                    initializer = new AssignStatement(rhs.getOrigin(), null, lhss, rhss, false);
                }

                return new VarDeclStmt(origin, null, List.of(localVariable), initializer);
            }
            case JCTree.JCWhileLoop whileLoop -> {
                return translateLoop(whileLoop, whileLoop.getCondition(), whileLoop.body, labels, x -> x);
            }
            case JCTree.JCForLoop forLoop -> {
                var loop = translateLoop(forLoop, forLoop.getCondition(), forLoop.body, labels, bodyStatements -> {
                    List<Statement> outerBody;
                    List<Statement> steps = compiler.translateStatements(forLoop.step);
                    if (loopsWithContinue.contains(forLoop)) {
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

                var initializer = compiler.translateStatements(forLoop.getInitializer());
                var result = new ArrayList<Statement>(initializer.size() + 1);
                result.addAll(initializer);
                result.add(loop);
                return new BlockStmt(origin, null, List.of(), result);
            }

            case JCTree.JCContinue jcContinue -> {
                Name targetLabel = null;
                int breakAndContinueCount = 0;
                var isContinue = true;

                if (jcContinue.label == null) {
                    if (outerLoop == null) {
                        throw new JavaViolationException();
                    } else {
                        loopsWithContinue.add(outerLoop);
                        if (outerLoop instanceof JCTree.JCForLoop forLoop) {
                            var label = getForLoopContinueLabel(forLoop);
                            targetLabel = compiler.getName(jcContinue, label);
                            isContinue = false;
                        } else {
                            breakAndContinueCount++;
                        }
                    }
                } else {
                    var loop = this.labelToLoop.get(jcContinue.label.toString());
                    loopsWithContinue.add(loop);
                    if (loop instanceof JCTree.JCForLoop forLoop) {
                        var label = getForLoopContinueLabel(forLoop);
                        targetLabel = compiler.getName(jcContinue, label);
                        isContinue = false;
                    } else {
                        targetLabel = compiler.getName(jcContinue, jcContinue.label);
                    }
                }
                return new BreakOrContinueStmt(origin, null, targetLabel, breakAndContinueCount, isContinue);
            }
            case JCTree.JCBreak jcBreak -> {
                Name targetLabel = null;
                int breakAndContinueCount = 0;
                if (jcBreak.label == null) {
                    breakAndContinueCount++;
                } else {
                    targetLabel = compiler.getName(jcBreak, jcBreak.label);
                }
                return new BreakOrContinueStmt(origin, null, targetLabel, breakAndContinueCount, false);
            }
            case JCTree.JCSkip _ -> {
                return null;
            }
            default -> {
            }
        }
        compiler.reportError(statement, "notSupported", statement.getClass().getSimpleName());
        return null;
    }

    private WhileStmt translateLoop(JCTree.JCStatement loop,
                                    JCTree.JCExpression condition,
                                    JCTree.JCStatement body,
                                    List<Label> labels,
                                    java.util.function.Function<List<Statement>, List<Statement>> transformBody) {
        var origin = compiler.toOrigin(loop);
        var header = new HeaderContainer();
        var postHeader = compiler.translateHeader(body, header);

        compiler.checkEmptyExpressions(loop, header.preconditions, "preconditions", "loop");
        compiler.checkEmptyExpressions(loop, header.postconditions, "postconditions", "loop");

        outerLoop = loop;
        for(var label : labels) {
            labelToLoop.put(label.getName(), loop);
        }

        var dafnyCondition = compiler.toExpr(condition);
        var bodyStatements = compiler.translateStatements(postHeader);
        var newBodyStatements = transformBody.apply(bodyStatements);
        return new WhileStmt(origin, null, labels, header.invariants, new Specification<>(header.decreases, null),
                new Specification<>(header.modifies, null), new BlockStmt(origin, null, List.of(), newBodyStatements),
                dafnyCondition);
    }

    private String getForLoopContinueLabel(JCTree.JCForLoop forLoop) {
        return forLoopContinueLabels.computeIfAbsent(forLoop, _ -> "generated" + compiler.generatedIndex++);
    }

    private @Nullable Statement translateExpressionStatement(JCTree.JCExpressionStatement statement) {
        var expr = statement.getExpression();
        var origin = compiler.toOrigin(expr);
        if (expr instanceof JCTree.JCMethodInvocation invocation) {
            var jverifyMethod = JavaToDafnyCompiler.getJVerifyMethod(invocation);
            if (jverifyMethod != null) {
                var name = jverifyMethod.getQualifiedName().toString();
                if (name.equals("check")) {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("Check should have a single argument");
                    }
                    return new AssertStmt(compiler.toOrigin(invocation), null,
                            compiler.toExpr(invocation.args.getFirst()), null);
                } else {
                    compiler.reportError(invocation, "contractAfterBody", jverifyMethod.getQualifiedName());
                    return null;
                }
            } else {
                if (invocation.getMethodSelect() instanceof JCTree.JCIdent ident && ident.name.contentEquals("super")) {
                    if (!invocation.getArguments().isEmpty()) {
                        compiler.reportError(invocation, "notSupported", "super calls with arguments");
                        return null;
                    }
                    return null;
                }
                var argBindings = invocation.getArguments().stream().map(a -> new ActualBinding(null, compiler.toExpr(a), false)).toList();
                ApplySuffix applySuffix = new ApplySuffix(compiler.toOrigin(invocation), compiler.toExpr(invocation.getMethodSelect()), null,
                        new ActualBindings(argBindings), null);
                return new AssignStatement(origin, null, List.of(),
                        List.of(new ExprRhs(applySuffix.getOrigin(), null, applySuffix)), false);
            }
        }
        if (expr instanceof JCTree.JCAssign assign) {
            List<Expression> lhss = List.of(compiler.toExpr(assign.getVariable()));
            List<AssignmentRhs> rhss = List.of(compiler.toAssignmentRhs(assign.getExpression()));
            return new AssignStatement(compiler.toOrigin(assign), null, lhss, rhss, false);
        }
        if (expr instanceof JCTree.JCAssignOp assignOp) {
            Expression target = compiler.toExpr(assignOp.getVariable());
            List<Expression> lhss = List.of(target);
            var operated = compiler.translateBinary(assignOp, assignOp.type, assignOp.getVariable().type, assignOp.getOperator(),
                    target, compiler.toExpr(assignOp.getExpression()));
            List<AssignmentRhs> rhss = List.of(new ExprRhs(origin, null, operated));
            return new AssignStatement(origin, null, lhss, rhss, false);
        }
        if (expr instanceof JCTree.JCUnary unary) {
            JCTree.Tag tag = unary.getTag();
            switch(tag) {
                case JCTree.Tag.POSTINC, POSTDEC, JCTree.Tag.PREINC, JCTree.Tag.PREDEC -> {
                    if (unary.type.getTag() == TypeTag.FLOAT || unary.type.getTag() == TypeTag.DOUBLE) {
                        compiler.reportError(unary, "notSupported", "operator " + unary.getOperator());
                        return null;
                    } else {
                        Expression target = compiler.toExpr(unary.getExpression());
                        List<Expression> lhss = List.of(target);

                        var opCode = (tag == JCTree.Tag.POSTINC || tag == JCTree.Tag.PREINC)
                                ? BinaryExprOpcode.Add : BinaryExprOpcode.Sub;
                        var incremented = new BinaryExpr(origin, opCode, target, new LiteralExpr(origin, 1));
                        List<AssignmentRhs> rhss = List.of(new ExprRhs(origin, null, incremented));
                        return new AssignStatement(origin, null, lhss, rhss, false);
                    }

                }
            }
        }
        compiler.reportError(statement, "notSupported", statement.getClass().getSimpleName());
        return null;
    }
}
