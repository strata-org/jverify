package com.aws.jverify.verifier.compiler;

import com.aws.jverify.common.Common;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.desugar.java.DesugarDoWhileLoop;
import com.aws.jverify.verifier.compiler.desugar.java.DesugarForLoop;
import com.aws.jverify.verifier.compiler.desugar.java.DesugarImpureStatementExpressions;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;

import java.util.*;
import java.util.stream.Collectors;

public class BlockCompiler {

    public final JavaToDafnyCompiler compiler;
    private final List<StatementSimplifier> statementSimplifiers = new ArrayList<>();

    public BlockCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
        statementSimplifiers.add(new DesugarForLoop(this));
        statementSimplifiers.add(new DesugarDoWhileLoop(this));
        statementSimplifiers.add(new DesugarImpureStatementExpressions(this));
    }

    private final Queue<Label> labels = new LinkedList<>();
    public final Map<String, JCTree.JCStatement> labelToLoop = new HashMap<>();
    public JCTree.JCStatement outerLoop;

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

        for(var simplifier : statementSimplifiers) {
            var result = simplifier.simplify(statement, labels);
            if (result != null) {
                return result;
            }
        }
        
        switch (statement) {
            case JCTree.JCExpressionStatement expressionStatement -> {
                return translateExpressionStatement(expressionStatement, originOverride);
            }
            case JCTree.JCAssert assertStmt -> {
                return List.of(new AssertStmt(origin, null, compiler.expressionCompiler.toExpr(assertStmt.getCondition()), null));
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
                return translateVariableDeclaration(origin, variableDecl);
            }
            case JCTree.JCWhileLoop whileLoop -> {
                return List.of(translateLoop(whileLoop, whileLoop.getCondition(), whileLoop.body, labels, x -> x));
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
        compiler.reportError(statement, "notSupported", "statement " + statement.getClass().getSimpleName());
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

    public List<Statement> translateContinue(JCTree.JCContinue jcContinue) {
        var origin = compiler.toOrigin(jcContinue);
        if (jcContinue.label == null) {
            if (outerLoop == null) {
                throw new JavaViolationException();
            } else {
                return List.of(new BreakOrContinueStmt(origin, null, null, 1, true));
            }
        } else {
            var targetLabel = compiler.getName(jcContinue, jcContinue.label);
            return List.of(new BreakOrContinueStmt(origin, null, targetLabel, 0, true));
        }
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
            var returnExpr = toAssignmentRhs(expr);
            var newLocalVarExpr = new NameSegment(exprOrigin, compiler.nameCompiler.METHOD_RETURN_VARIABLE_NAME, null);
            var assignment = new AssignStatement(exprOrigin, null, List.of(newLocalVarExpr), List.of(returnExpr), false);
            var returnStmt = new ReturnStmt(origin, null, null);
            return List.of(assignment,returnStmt);
        }
    }

    private List<Statement> translateIfStatement(JCTree.JCIf ifStatement) {
        var origin = compiler.toOrigin(ifStatement);
        var condition = compiler.expressionCompiler.toExpr(ifStatement.getCondition());
        var thenBranch = blockifyStatements(origin, translateStatement(ifStatement.getThenStatement()));
        BlockStmt elseBranch = null;
        if (ifStatement.getElseStatement() != null) {
            elseBranch = blockifyStatements(origin, translateStatement(ifStatement.getElseStatement()));
        }
        return List.of(new IfStmt(origin, null, List.of(), false, condition,
                thenBranch, elseBranch));
    }

    private List<Statement> translateVariableDeclaration(IOrigin origin, JCTree.JCVariableDecl variableDecl) {
        LocalVariable localVariable = new LocalVariable(origin, variableDecl.name.toString(),
                compiler.translateType(variableDecl.getModifiers(), variableDecl.getType().type, origin), false);
        ConcreteAssignStatement dafnyInitializer = null;
        if (variableDecl.getInitializer() != null) {
            var rhs = toAssignmentRhs(variableDecl.getInitializer());
            List<Expression> lhss = List.of(new IdentifierExpr(localVariable.getOrigin(), localVariable.getName()));
            List<AssignmentRhs> rhss = List.of(rhs);
            dafnyInitializer = new AssignStatement(origin, null, lhss, rhss, false);
        }

        return List.of(new VarDeclStmt(origin, null, List.of(localVariable), dafnyInitializer));
    }

    public WhileStmt translateLoop(JCTree.JCStatement loop,
                                   JCTree.JCExpression condition,
                                   JCTree.JCStatement body,
                                   List<Label> labels,
                                   java.util.function.Function<List<Statement>, List<Statement>> transformBody) {
        var origin = compiler.toOrigin(loop);
        var header = new MethodOrLoopContract(loop, false);
        var postHeader = translateHeader(body, header, true);

        checkLoopHeaderAndSetupLabels(loop, labels, header);

        var dafnyCondition = compiler.expressionCompiler.toExpr(condition);
        var bodyStatements = translateStatements(postHeader);
        var newBodyStatements = transformBody.apply(bodyStatements);
        return new WhileStmt(origin, null, labels, header.invariants, new Specification<>(header.decreases, null),
                new Specification<>(header.modifies, null), new BlockStmt(origin, null, List.of(), newBodyStatements),
                dafnyCondition);
    }

    private void checkLoopHeaderAndSetupLabels(JCTree.JCStatement loop, List<Label> labels, MethodOrLoopContract header) {
        checkEmptyExpressions(loop, header.preconditions, "preconditions", "loop");
        checkEmptyExpressions(loop, header.postconditions, "postconditions", "loop");

        outerLoop = loop;
        for(var label : labels) {
            labelToLoop.put(label.getName(), loop);
        }
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
            default -> {
                compiler.reportError(statement, "notSupported", "expression statement with expr " + expr.getClass().getSimpleName());
                return List.of();
            }
        }
    }

    private List<Statement> translateAssign(JCTree.JCAssign assign, IOrigin originOverride) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> compiler.toOrigin(assign));
        List<Expression> lhss = List.of(compiler.expressionCompiler.toExpr(assign.getVariable(), originOverride));
        List<AssignmentRhs> rhss = List.of(toAssignmentRhs(assign.getExpression(), originOverride));
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
                    compiler.expressionCompiler.toExpr(invocation.args.getFirst()), null));
        } else {
            compiler.reportError(invocation, "contractAfterBody", jverifyMethod.getQualifiedName());
            return List.of();
        }
    }

    private List<Statement> translateVanillaJavaMethodInvocation(JCTree.JCMethodInvocation invocation) {
        var origin = compiler.toOrigin(invocation);
        if (invocation.getMethodSelect() instanceof JCTree.JCIdent ident && ident.name == ident.name.table.names._super) {
            Symbol.MethodSymbol baseConstructor = (Symbol.MethodSymbol) ident.sym;

            if (!compiler.symbolsWithAContract.contains(baseConstructor)) {
                return List.of();
            }

            var baseConstructorName = compiler.nameCompiler.getCompiledName(baseConstructor);
            var baseConstructorClassName = compiler.nameCompiler.getCompiledName(baseConstructor.enclClass());
            var initName = compiler.nameCompiler.getInitMethodName(baseConstructorClassName, baseConstructorName);
            var arguments = invocation.getArguments().stream().map(
                    e -> new ActualBinding(null, compiler.expressionCompiler.toExpr(e), false)).toList();
            var applySuffix = new ApplySuffix(origin,
                    new NameSegment(origin, initName, null), null, new ActualBindings(arguments), null);
            var initCall = new AssignStatement(origin, null, List.of(),
                    List.of(new ExprRhs(applySuffix.getOrigin(), null, applySuffix)), false);

            return List.of(initCall);
        }
        var argBindings = invocation.getArguments().stream().map(
                a -> new ActualBinding(null, compiler.expressionCompiler.toExpr(a), false)).toList();
        Expression expr = compiler.expressionCompiler.toExpr(invocation.getMethodSelect());
        ApplySuffix applySuffix = new ApplySuffix(origin, expr, null,
                new ActualBindings(argBindings), null);
        return List.of(new AssignStatement(origin, null, List.of(),
                List.of(new ExprRhs(applySuffix.getOrigin(), null, applySuffix)), false));
    }

    public List<Statement> translateSwitchStatement(JCTree.JCSwitch switchStmt) {
        var origin = compiler.toOrigin(switchStmt);
        var patternBodies = new Patterns(compiler).translateSwitchLabels(switchStmt);
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
                    origin, Patterns.makeWildPattern(origin), List.of(), null));
        }

        var source = compiler.expressionCompiler.toExpr(switchStmt.getExpression());
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
     * @see #translateHeader(List, MethodOrLoopContract, boolean)
     */
    public List<JCTree.JCStatement> translateHeader(JCTree.JCStatement statement, MethodOrLoopContract header, boolean reportErrors) {
        var statements = statement instanceof JCTree.JCBlock block
                ? block.getStatements()
                : List.of(statement);
        return translateHeader(statements, header, reportErrors);
    }

    /**
     * Translates header statements from the start of {@code statements}
     * until the first non-header statement or the end of the list,
     * appending the translations to the given {@link MethodOrLoopContract},
     * and returning a list view of the remaining statements.
     *
     * <p>NOTE: The list view is constructed using {@link List#subList(int, int)} and has the corresponding caveats;
     * namely, that it is backed by the original list.
     */
    public List<JCTree.JCStatement> translateHeader(List<JCTree.JCStatement> statements,
                                                    MethodOrLoopContract header,
                                                    boolean reportErrors) {
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
                    header.preconditions.add(new AttributedExpression(compiler.expressionCompiler.toExpr(invocation.getArguments().getFirst()), null, null));
                }
                case "postcondition" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A postcondition call may have only one argument");
                    }
                    var first = invocation.getArguments().getFirst();
                    if (first instanceof JCTree.JCLambda lambda) {
                        if (lambda.getParameters().size() != 1) {
                            throw new JavaViolationException("A postcondition call lambda may take only one argument");
                        }
                        var parameter = lambda.params.getFirst();
                        var origin = compiler.toOrigin(lambda);
                        var paramName = parameter.getName().toString();
                        var type = compiler.translateType(null, parameter.type, compiler.toOrigin(parameter));

                        var returnVar = new BoundVar(origin, new Name(origin, paramName), type, false);
                        var lhs = new CasePattern<>(origin, paramName, returnVar, null);
                        var rhs = TreeInfo.isConstructor(header.treeOrigin)
                                ? new ThisExpr(origin)
                                : new NameSegment(origin, compiler.nameCompiler.METHOD_RETURN_VARIABLE_NAME, null);
                        var origCondition = compiler.expressionCompiler.toExpr(lambda.getBody());
                        var condition = new LetExpr(origin, List.of(lhs), List.of(rhs), origCondition, true, null);
                        header.postconditions.add(new AttributedExpression(condition, null, null));
                        
                    } else if (first instanceof JCTree.JCMemberReference memberReference) {
                        var origin = compiler.toOrigin(memberReference);
                        var argBindings = List.of(new ActualBinding(null,
                                new NameSegment(origin, compiler.nameCompiler.METHOD_RETURN_VARIABLE_NAME, null), false));
                        var callee = new ExprDotName(origin,
                                compiler.expressionCompiler.toExpr(memberReference.expr),
                                compiler.getName(memberReference, memberReference.name), null);
                        var call = new ApplySuffix(origin, callee, null,
                                new ActualBindings(argBindings), null);
                        header.postconditions.add(new AttributedExpression(call, null, null));
                    } else {
                        var dafnyExpr = compiler.expressionCompiler.toExpr(first);
                        header.postconditions.add(new AttributedExpression(dafnyExpr, null, null));
                    }
                }
                case "invariant" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("invariant should have a single argument");
                    }
                    header.invariants.add(new AttributedExpression(compiler.expressionCompiler.toExpr(invocation.getArguments().getFirst()), null, null));
                }
                case "decreases" -> {
                    for(var decrease : invocation.getArguments()) {
                        header.decreases.add(compiler.expressionCompiler.toExpr(decrease));
                    }
                }
                case "reads" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A reads call must have exactly one argument");
                    }
                    var origExpr = invocation.getArguments().getFirst();
                    var origin = compiler.toOrigin(origExpr);
                    var expr = compiler.expressionCompiler.toExpr(origExpr);
                    header.reads.add(new FrameExpression(origin, expr, null));
                }
                case "modifies" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A modifies call must have exactly one argument");
                    }
                    var origExpr = invocation.getArguments().getFirst();
                    var origin = compiler.toOrigin(origExpr);
                    var expr = compiler.expressionCompiler.toExpr(origExpr);
                    header.modifies.add(new FrameExpression(origin, expr, null));
                }
                default -> {
                    if (reportErrors) {
                        compiler.reportError(invocation, "notSupported", methodName);
                    }
                    return null;
                }
            }
            headerStatements++;
        }
        var postHeaderStatements = new ArrayList<>(statements.subList(headerStatements, statements.size()));
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



    public AssignmentRhs toAssignmentRhs(JCTree.JCExpression expr) {
        return toAssignmentRhs(expr, null);
    }

    public AssignmentRhs toAssignmentRhs(JCTree.JCExpression expr, IOrigin originOverride) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> compiler.toOrigin(expr));
        switch (expr) {
            case JCTree.JCNewClass newClass -> {
                if (((Symbol.ClassSymbol) TreeInfo.symbol(newClass.clazz)).isRecord()) {
                    var datatypeValue = compiler.expressionCompiler.translateNewRecord(origin, newClass);
                    return new ExprRhs(origin, null, datatypeValue);
                }
                String ctorNameStr = compiler.nameCompiler.getCompiledName(newClass.constructor);
                Name ctorName = new Name(origin, ctorNameStr);
                var baseType = (NameSegment)compiler.expressionCompiler.toExpr(newClass.clazz);
                var classBaseType = new NameSegment(baseType.getOrigin(), compiler.nameCompiler.CLASS_PREFIX + baseType.getName(), baseType.getOptTypeArguments());
                var ty = new UserDefinedType(origin, new ExprDotName(origin, classBaseType, ctorName, null));

                var argBindings = newClass.getArguments().stream()
                        .map(a -> new ActualBinding(
                                null, compiler.expressionCompiler.toExpr(a), false))
                        .toList();
                return new AllocateClass(origin, null, ty, new ActualBindings(argBindings));
            }
            case JCTree.JCNewArray newArray -> {
                var arrayDimensions = newArray.getDimensions().stream().map(compiler.expressionCompiler::toExpr).toList();
                var arrayInitializers = newArray.getInitializers();
                var arrayJavaType = newArray.getType();
                if (arrayJavaType instanceof JCTree.JCArrayTypeTree _) {
                    compiler.reportError(expr, "notSupported", "multi-dimensional arrays");
                }
                var arrayDafnyType = compiler.translateType(null, arrayJavaType.type, compiler.toOrigin(arrayJavaType));

                if (arrayInitializers != null && !arrayInitializers.isEmpty()) {
                    compiler.reportError(expr, "notSupported", "new array with initializers");
                }
                return new AllocateArray(origin, null, arrayDafnyType, arrayDimensions, null);
            }
            default -> {
            }
        }
        var dafnyExpr = compiler.expressionCompiler.toExpr(expr, originOverride);
        return new ExprRhs(origin, null, dafnyExpr);
    }
}
