package com.aws.jverify.verifier;

import com.aws.jverify.common.Common;
import com.aws.jverify.generated.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;

import java.util.*;
import java.util.stream.Collectors;

public class MethodCompiler {

    private final JavaToDafnyCompiler compiler;

    public MethodCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
    }

    private final Queue<Label> labels = new LinkedList<>();
    private final Map<String, JCTree.JCStatement> labelToLoop = new HashMap<>();
    private final Map<JCTree.JCStatement, String> forLoopContinueLabels = new HashMap<>();
    private final Set<JCTree.JCStatement> forLoopsWithContinue = new HashSet<>();
    private JCTree.JCStatement outerLoop;

    public int generatedIndex = 0;

    public List<Statement> translateStatement(JCTree.JCStatement statement) {
        return translateStatement(statement, null);
    }

    public List<Statement> translateStatement(JCTree.JCStatement statement, IOrigin originOverride) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> compiler.toOrigin(statement));
        if (statement instanceof JCTree.JCLabeledStatement labeledStatement) {
            labels.add(new Label(origin, labeledStatement.getLabel().toString()));
            return translateStatement(labeledStatement.getStatement());
        }
        var labels = this.labels.stream().toList();
        this.labels.clear();

        switch (statement) {
            case JCTree.JCExpressionStatement expressionStatement -> {
                return translateExpressionStatement(expressionStatement, originOverride);
            }
            case JCTree.JCAssert assertStmt -> {
                return List.of(new AssertStmt(origin, null, compiler.toExpr(assertStmt.getCondition()), null));
            }
            case JCTree.JCIf ifStatement -> {
                return translateIfStatement(ifStatement);
            }
            case JCTree.JCBlock blockStatement -> {
                return List.of(new BlockStmt(origin, null, List.of(),
                        translateStatements(blockStatement.getStatements())));
            }
            case JCTree.JCReturn returnStatement -> {
                return translateReturn(returnStatement);
            }
            case JCTree.JCVariableDecl variableDecl -> {
                return translateVariableDeclaration(origin, variableDecl.getName().toString(), variableDecl.getType(), variableDecl.getInitializer());
            }
            case JCTree.JCWhileLoop whileLoop -> {
                return List.of(translateLoop(whileLoop, whileLoop.getCondition(), whileLoop.body, labels, x -> x));
            }
            case JCTree.JCDoWhileLoop doWhileLoop -> {
                return translateDoWhileLoop(doWhileLoop, labels);
            }
            case JCTree.JCForLoop forLoop -> {
                return translateForLoop(forLoop, labels);
            }

            case JCTree.JCContinue jcContinue -> {
                return translateContinue(jcContinue);
            }
            case JCTree.JCBreak jcBreak -> {
                return translateBreak(jcBreak);
            }
            case JCTree.JCSwitch jcSwitch -> {
                return translateSwitchStatement(jcSwitch);
            }
            case JCTree.JCSkip _ -> {
                return List.of();
            }
            default -> {
            }
        }
        compiler.reportError(statement, "notSupported", statement.getClass().getSimpleName());
        return List.of();
    }

    private List<Statement> translateBreak(JCTree.JCBreak jcBreak) {
        var origin = compiler.toOrigin(jcBreak);
        Name targetLabel = null;
        int breakAndContinueCount = 0;
        if (jcBreak.label == null) {
            breakAndContinueCount++;
        } else {
            targetLabel = compiler.getName(jcBreak, jcBreak.label);
        }
        return List.of(new BreakOrContinueStmt(origin, null, targetLabel, breakAndContinueCount, false));
    }

    private List<Statement> translateContinue(JCTree.JCContinue jcContinue) {
        var origin = compiler.toOrigin(jcContinue);
        if (jcContinue.label == null) {
            if (outerLoop == null) {
                throw new JavaViolationException();
            } else {
                if (outerLoop instanceof JCTree.JCForLoop forLoop) {
                    return translateContinueForForLoop(jcContinue, forLoop);
                } else {
                    return List.of(new BreakOrContinueStmt(origin, null, null, 1, true));
                }
            }
        } else {
            var loop = this.labelToLoop.get(jcContinue.label.toString());
            if (loop instanceof JCTree.JCForLoop forLoop) {
                return translateContinueForForLoop(jcContinue, forLoop);
            } else {
                var targetLabel = compiler.getName(jcContinue, jcContinue.label);
                return List.of(new BreakOrContinueStmt(origin, null, targetLabel, 0, true));
            }
        }
    }

    /**
     * For for-loops, the continue statement must jump to before the increment, instead of to before the guard 
     */
    private List<Statement> translateContinueForForLoop(JCTree.JCContinue jcContinue, JCTree.JCForLoop forLoop) {
        var origin = compiler.toOrigin(jcContinue);
        forLoopsWithContinue.add(forLoop);
        var label = getForLoopContinueLabel(forLoop);
        return List.of(new BreakOrContinueStmt(origin, null, compiler.getName(jcContinue, label), 
                0, false));
    }

    private List<Statement> translateReturn(JCTree.JCReturn returnStatement) {
        var origin = compiler.toOrigin(returnStatement);
        var expr = returnStatement.getExpression();
        if (expr == null) {
            return List.of(new ReturnStmt(origin, null, null));
        } else {
            // Replace
            //   return e;
            // by
            //   var tmp := e;
            //   return tmp;
            // so that we can have allocation in e.
            var exprOrigin = compiler.toOrigin(expr);
            var returnExpr = compiler.toAssignmentRhs(expr);
            var newLocalVarName = getTmpVariableName();
            var newLocalVar = new LocalVariable(exprOrigin,
                    newLocalVarName, null, false);
            var newLocalVarExpr = new NameSegment(exprOrigin, newLocalVarName, null);
            var varDeclStmt = new VarDeclStmt(exprOrigin, null, List.of(newLocalVar), null);
            var assignment = new AssignStatement(exprOrigin, null, List.of(newLocalVarExpr), List.of(returnExpr), false);
            var returnStmt = new ReturnStmt(origin, null, List.of(new ExprRhs(exprOrigin, null,newLocalVarExpr)));
            return List.of(varDeclStmt, assignment,returnStmt);
        }
    }

    private List<Statement> translateIfStatement(JCTree.JCIf ifStatement) {
        var origin = compiler.toOrigin(ifStatement);
        var condition = compiler.toExpr(ifStatement.getCondition());
        var thenBranch = blockifyStatements(origin, translateStatement(ifStatement.getThenStatement()));
        BlockStmt elseBranch = null;
        if (ifStatement.getElseStatement() != null) {
            elseBranch = blockifyStatements(origin, translateStatement(ifStatement.getElseStatement()));
        }
        return List.of(new IfStmt(origin, null, List.of(), false, condition,
                thenBranch, elseBranch));
    }

    private List<Statement> translateForLoop(JCTree.JCForLoop forLoop, List<Label> labels) {
        var origin = compiler.toOrigin(forLoop);
        var loop = translateLoop(forLoop, forLoop.getCondition(), forLoop.body, labels, bodyStatements -> {
            List<Statement> outerBody;
            List<Statement> steps = translateStatements(forLoop.step);
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

        var initializer = translateStatements(forLoop.getInitializer());
        var result = new ArrayList<Statement>(initializer.size() + 1);
        result.addAll(initializer);
        result.add(loop);
        // Pack the statements in a block to ensure the loop index variable is
        // scoped
        return List.of(new BlockStmt(origin, null, List.of(), result));
    }

    private List<Statement> translateDoWhileLoop(JCTree.JCDoWhileLoop doWhileLoop, List<Label> labels) {
        var whileLoop = translateLoop(doWhileLoop, doWhileLoop.getCondition(), doWhileLoop.body, labels, x -> x);
        var firstBlock = whileLoop.getBody();
        return List.of(firstBlock, whileLoop);
    }

    private List<Statement> translateVariableDeclaration(IOrigin origin, String string, JCTree type, JCTree.JCExpression initializer) {
        LocalVariable localVariable = new LocalVariable(origin,
                string, compiler.toType(type.type, false, origin), false);
        ConcreteAssignStatement dafnyInitializer = null;
        if (initializer != null) {
            var rhs = compiler.toAssignmentRhs(initializer);
            List<Expression> lhss = List.of(new IdentifierExpr(localVariable.getOrigin(), localVariable.getName()));
            List<AssignmentRhs> rhss = List.of(rhs);
            dafnyInitializer = new AssignStatement(origin, null, lhss, rhss, false);
        }

        return List.of(new VarDeclStmt(origin, null, List.of(localVariable), dafnyInitializer));
    }

    private WhileStmt translateLoop(JCTree.JCStatement loop,
                                    JCTree.JCExpression condition,
                                    JCTree.JCStatement body,
                                    List<Label> labels,
                                    java.util.function.Function<List<Statement>, List<Statement>> transformBody) {
        var origin = compiler.toOrigin(loop);
        var header = new HeaderContainer();
        var postHeader = translateHeader(body, header);

        checkLoopHeaderAndSetupLabels(loop, labels, header);

        var dafnyCondition = compiler.toExpr(condition);
        var bodyStatements = translateStatements(postHeader);
        var newBodyStatements = transformBody.apply(bodyStatements);
        return new WhileStmt(origin, null, labels, header.invariants, new Specification<>(header.decreases, null),
                new Specification<>(header.modifies, null), new BlockStmt(origin, null, List.of(), newBodyStatements),
                dafnyCondition);
    }

    private void checkLoopHeaderAndSetupLabels(JCTree.JCStatement loop, List<Label> labels, HeaderContainer header) {
        checkEmptyExpressions(loop, header.preconditions, "preconditions", "loop");
        checkEmptyExpressions(loop, header.postconditions, "postconditions", "loop");

        outerLoop = loop;
        for(var label : labels) {
            labelToLoop.put(label.getName(), loop);
        }
    }

    private String getForLoopContinueLabel(JCTree.JCForLoop forLoop) {
        return forLoopContinueLabels.computeIfAbsent(forLoop, _ -> "$loop" + generatedIndex++);
    }
    
    private String getTmpVariableName() {
        return "#_tmpVar_"+(generatedIndex++);
    }

    private List<Statement> translateExpressionStatement(JCTree.JCExpressionStatement statement, IOrigin originOverride) {
        var expr = statement.getExpression();
        switch (expr) {
            case JCTree.JCMethodInvocation invocation -> {
                return translateStatementMethodInvocation(invocation);
            }
            case JCTree.JCAssign assign -> {
                return translateAssign(assign, originOverride);
            }
            case JCTree.JCAssignOp assignOp -> {
                return translateAssignOp(assignOp);
            }
            case JCTree.JCUnary unary -> {
                return translateUnaryExpressionStatement(unary);
            }
            default -> {
                compiler.reportError(statement, "notSupported", statement.getClass().getSimpleName());
                return List.of();
            }
        }
    }

    private List<Statement> translateUnaryExpressionStatement(JCTree.JCUnary unary) {
        var origin = compiler.toOrigin(unary);
        var tag = unary.getTag();
        switch (tag) {
            case JCTree.Tag.POSTINC, POSTDEC, JCTree.Tag.PREINC, JCTree.Tag.PREDEC -> {
                if (unary.type.getTag() == TypeTag.FLOAT || unary.type.getTag() == TypeTag.DOUBLE) {
                    compiler.reportError(unary, "notSupported", "operator " + unary.getOperator());
                    return List.of();
                } else {
                    Expression target = compiler.toExpr(unary.getExpression());
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

    private List<Statement> translateAssignOp(JCTree.JCAssignOp assignOp) {
        var origin = compiler.toOrigin(assignOp);
        Expression target = compiler.toExpr(assignOp.getVariable());
        List<Expression> lhss = List.of(target);
        var operated = compiler.translateBinary(assignOp, assignOp.type, assignOp.getVariable().type, assignOp.getOperator(),
                target, compiler.toExpr(assignOp.getExpression()));
        List<AssignmentRhs> rhss = List.of(new ExprRhs(origin, null, operated));
        return List.of(new AssignStatement(origin, null, lhss, rhss, false));
    }

    private List<Statement> translateAssign(JCTree.JCAssign assign, IOrigin originOverride) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> compiler.toOrigin(assign));
        List<Expression> lhss = List.of(compiler.toExpr(assign.getVariable(), originOverride));
        List<AssignmentRhs> rhss = List.of(compiler.toAssignmentRhs(assign.getExpression(), originOverride));
        return List.of(new AssignStatement(origin, null, lhss, rhss, false));
    }

    private List<Statement> translateStatementMethodInvocation(JCTree.JCMethodInvocation invocation) {
        var jverifyMethod = JavaToDafnyCompiler.getJVerifyMethod(invocation);
        if (jverifyMethod != null) {
            return translateJVerifyMethodInvocation(invocation, jverifyMethod);
        } else {
            return translateVanillaJavaMethodInvocation(invocation);
        }
    }

    private List<Statement> translateJVerifyMethodInvocation(JCTree.JCMethodInvocation invocation, Symbol.MethodSymbol jverifyMethod) {
        var name = jverifyMethod.getQualifiedName().toString();
        if (name.equals("check")) {
            if (invocation.args.size() != 1) {
                throw new JavaViolationException("Check should have a single argument");
            }
            return List.of(new AssertStmt(compiler.toOrigin(invocation), null,
                    compiler.toExpr(invocation.args.getFirst()), null));
        } else {
            compiler.reportError(invocation, "contractAfterBody", jverifyMethod.getQualifiedName());
            return List.of();
        }
    }

    private List<Statement> translateVanillaJavaMethodInvocation(JCTree.JCMethodInvocation invocation) {
        var origin = compiler.toOrigin(invocation);
        if (invocation.getMethodSelect() instanceof JCTree.JCIdent ident && ident.name.contentEquals("super")) {
            if (!invocation.getArguments().isEmpty()) {
                compiler.reportError(invocation, "notSupported", "super calls with arguments");
                return List.of();
            }
            return List.of();
        }
        var argBindings = invocation.getArguments().stream().map(a -> new ActualBinding(null, compiler.toExpr(a), false)).toList();
        Expression expr = compiler.toExpr(invocation.getMethodSelect());
        ApplySuffix applySuffix = new ApplySuffix(origin, expr, null,
                new ActualBindings(argBindings), null);
        return List.of(new AssignStatement(origin, null, List.of(),
                List.of(new ExprRhs(applySuffix.getOrigin(), null, applySuffix)), false));
    }

    public List<Statement> translateSwitchStatement(JCTree.JCSwitch switchStmt) {
        var origin = compiler.toOrigin(switchStmt);
        var patternBodies = compiler.translateSwitchLabels(switchStmt);
        if (patternBodies == null) {
            return List.of();
        }

        var translatedCases = patternBodies.stream().map(patternBody -> {
            var caseOrigin = compiler.toOrigin(patternBody.cas());
            var body = patternBody.body();

            // A switch rule introduces either an expression, a block, or a throw statement.
            // Within a switch statement, a switch rule expression must be a statement expression.
            List<Statement> translatedBody = switch (body) {
                case JCTree.JCExpressionStatement bodyStatement -> translateStatement(bodyStatement);
                case JCTree.JCBlock bodyBlock -> translateStatement(bodyBlock);
                case JCTree.JCThrow ignored -> {
                    compiler.reportError(body, "notSupported", "switch rule throw");
                    yield List.of();
                }
                default -> throw new JavaViolationException();
            };
            return new NestedMatchCaseStmt(caseOrigin, patternBody.pattern(), translatedBody, null);
        }).collect(Collectors.toCollection(ArrayList::new));

        // The switch statement may not be exhaustive, but a Dafny match statement must be,
        // so we add a catch-all no-op case to ensure the translated match is exhaustive.
        // (It would be safe to add this case unconditionally, but Dafny would warn that the case is redundant.)
        if (!switchStmt.isExhaustive) {
            translatedCases.add(new NestedMatchCaseStmt(
                    origin, JavaToDafnyCompiler.makeWildPattern(origin), List.of(), null));
        }

        var source = compiler.toExpr(switchStmt.getExpression());
        return List.of(new NestedMatchStmt(origin, null, source, translatedCases, true));
    }

    public <T extends JCTree.JCStatement> List<Statement> translateStatements(List<T> statements) {
        return translateStatements(statements, null);
    }

    public <T extends JCTree.JCStatement> List<Statement> translateStatements(List<T> statements, IOrigin originOverride) {
        return statements.stream().flatMap(s -> translateStatement(s, originOverride).stream()).toList();
    }

    public void checkEmptyExpressions(JCTree tree,
                                      List<AttributedExpression> expressions,
                                      String typeName,
                                      String containerName) {
        for (var _ : expressions) {
            compiler.reportError(tree, "wrongContract", typeName, containerName);
        }
    }

    /**
     * @see #translateHeader(List, HeaderContainer)
     */
    public List<JCTree.JCStatement> translateHeader(JCTree.JCStatement statement, HeaderContainer header) {
        var statements = statement instanceof JCTree.JCBlock block
                ? block.getStatements()
                : List.of(statement);
        return translateHeader(statements, header);
    }

    /**
     * Translates header statements from the start of {@code statements}
     * until the first non-header statement or the end of the list,
     * appending the translations to the given {@link HeaderContainer},
     * and returning a list view of the remaining statements.
     *
     * <p>NOTE: The list view is constructed using {@link List#subList(int, int)} and has the corresponding caveats;
     * namely, that it is backed by the original list.
     */
    public List<JCTree.JCStatement> translateHeader(List<JCTree.JCStatement> statements, HeaderContainer header) {
        var headerStatements = 0;
        JCTree.JCStatement callToSuper = null;
        statementLoop: for (var statement : statements) {
            if (!(statement instanceof JCTree.JCExpressionStatement expressionStatement
                    && expressionStatement.getExpression() instanceof JCTree.JCMethodInvocation invocation)) {
                break;
            }
            var jverifyMethod = JavaToDafnyCompiler.getJVerifyMethod(invocation);
            var isSuper = (invocation.getMethodSelect() instanceof JCTree.JCIdent ident && ident.name.contentEquals("super"));
            if (isSuper) {
                callToSuper = statement;
                headerStatements++;
                continue;
            }
            if (jverifyMethod == null) {
                break;
            }
            var methodName = jverifyMethod.getQualifiedName().toString();
            switch (methodName) {
                case "check" -> {
                    // not a header method, so stop here
                    break statementLoop;
                }
                case Common.PRECONDITION -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A precondition call may have only one argument");
                    }
                    header.preconditions.add(new AttributedExpression(compiler.toExpr(invocation.getArguments().getFirst()), null, null));
                }
                case "postcondition" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("An postcondition call may have only one argument");
                    }
                    var first = invocation.getArguments().getFirst();
                    if (first instanceof JCTree.JCLambda lambda) {
                        if (lambda.getParameters().size() != 1) {
                            throw new JavaViolationException("An ensures call lambda may take only one argument");
                        }
                        var parameter = lambda.getParameters().getFirst();
                        header.returnNames.add(new Name(compiler.toOrigin(lambda), parameter.getName().toString()));
                        var postconditionPredicate = compiler.toExpr(lambda.getBody());
                        if (postconditionPredicate != null) {
                            header.postconditions.add(new AttributedExpression(postconditionPredicate, null, null));
                        }
                    } else {
                        var dafnyExpr = compiler.toExpr(first);
                        header.postconditions.add(new AttributedExpression(dafnyExpr, null, null));
                    }
                }
                case "invariant" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("invariant should have a single argument");
                    }
                    header.invariants.add(new AttributedExpression(compiler.toExpr(invocation.getArguments().getFirst()), null, null));
                }
                case "decreases" -> {
                    for(var decrease : invocation.getArguments()) {
                        header.decreases.add(compiler.toExpr(decrease));
                    }
                }
                case "reads" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A reads call must have exactly one argument");
                    }
                    var origExpr = invocation.getArguments().getFirst();
                    var origin = compiler.toOrigin(origExpr);
                    var expr = compiler.toExpr(origExpr);
                    header.reads.add(new FrameExpression(origin, expr, null));
                }
                case "modifies" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A modifies call must have exactly one argument");
                    }
                    var origExpr = invocation.getArguments().getFirst();
                    var origin = compiler.toOrigin(origExpr);
                    var expr = compiler.toExpr(origExpr);
                    header.modifies.add(new FrameExpression(origin, expr, null));
                }
                default -> {
                    compiler.reportError(invocation, "notSupported", methodName);
                    return null;
                }
            }
            headerStatements++;
        }
        var postHeaderStatements = new ArrayList<JCTree.JCStatement>(statements.subList(headerStatements, statements.size()));
        if (callToSuper != null) {
            postHeaderStatements.addFirst(callToSuper);
        }
        return postHeaderStatements;
    }

    /**
     * Returns the specified statement as-is if it's already a {@link BlockStmt},
     * or wraps it in a singleton block otherwise.
     */
    public static BlockStmt blockifyStatements(IOrigin origin,  List<Statement> statements) {
        return statements.getFirst() instanceof BlockStmt block
                ? block
                : new BlockStmt(origin, null, List.of(), statements);
    }

}
