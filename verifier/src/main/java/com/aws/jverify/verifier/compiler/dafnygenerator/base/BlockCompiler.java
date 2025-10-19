package com.aws.jverify.verifier.compiler.dafnygenerator.base;

import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.simplifications.MethodOrLoopContract;
import com.aws.jverify.verifier.compiler.dafnygenerator.*;
import com.aws.jverify.verifier.compiler.simplifications.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;

import java.util.*;
import java.util.stream.Collectors;

public class BlockCompiler {
    public final BaseDafnyGenerator baseGenerator;
    public final DafnyGenerator generator;
    public final Reporter reporter;
    private final ExpressionCompiler expressionCompiler;
    private final NameCompiler nameCompiler;
    MethodOrLoopContractCompiler methodOrLoopContractCompiler;
    private final Symbol.MethodSymbol methodSymbol;
    private final List<StatementCompiler> statementCompilers = new ArrayList<>();

    public BlockCompiler(BaseDafnyGenerator compiler, Symbol.MethodSymbol methodSymbol) {
        this.generator = compiler.context.get(DafnyGenerator.class);
        baseGenerator = compiler;
        nameCompiler = NameCompiler.instance(compiler.context);
        reporter = compiler.reporter;
        expressionCompiler = compiler.expressionCompiler;
        this.methodSymbol = methodSymbol;
        methodOrLoopContractCompiler = MethodOrLoopContractCompiler.instance(compiler.context);
        statementCompilers.add(new ForLoopCompiler(this));
        statementCompilers.add(new DoWhileLoopCompiler(this));
    }

    private final Queue<Label> labels = new LinkedList<>();
    public final Map<String, JCTree.JCStatement> labelToLoop = new HashMap<>();
    public JCTree.JCStatement outerLoop;

    public int generatedIndex = 0;

    public List<Statement> translateStatement(JCTree.JCStatement statement) {
        return translateStatement(statement, null);
    }

    public List<Statement> translateStatement(JCTree.JCStatement statement, IOrigin originOverride) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> reporter.toOrigin(statement));
        if (statement instanceof JCTree.JCLabeledStatement labeledStatement) {
            labels.add(new Label(origin, labeledStatement.getLabel().toString()));
            return translateStatement(labeledStatement.getStatement());
        }
        var labels = this.labels.stream().toList();
        this.labels.clear();
        
        return generator.translateStatementAfterLabel(this, statement, labels, origin);
    }

    public List<Statement> translateStatementAfterlabel(JCTree.JCStatement statement, List<Label> labels, IOrigin origin) {
        List<Statement> statements = new ArrayList<>();
        var expressionContext = new ExpressionContext(statements::add, true, this, null);
        for (var compiler : statementCompilers) {
            var result = compiler.compile(statement, labels, expressionContext);
            if (result != null) {
                return result;
            }
        }

        List<Statement> rhsStatements = switch (statement) {
            case JCTree.JCExpressionStatement expressionStatement ->
                    translateExpressionStatement(expressionStatement, origin, expressionContext);
            case JCTree.JCAssert assertStmt ->
                    List.of(new AssertStmt(origin, null, expressionCompiler.toExpr(assertStmt.getCondition(), expressionContext), null));
            case JCTree.JCIf ifStatement -> translateIfStatement(ifStatement, expressionContext);
            case JCTree.JCBlock blockStatement -> List.of(new BlockStmt(origin, null, List.of(),
                    translateStatements(blockStatement.getStatements())));
            case JCTree.JCReturn returnStatement -> translateReturn(returnStatement, expressionContext);
            case JCTree.JCVariableDecl variableDecl ->
                    translateVariableDeclaration(origin, variableDecl, expressionContext);
            case JCTree.JCWhileLoop whileLoop ->
                    List.of(translateLoop(whileLoop, whileLoop.getCondition(), whileLoop.body, labels, x -> x, expressionContext));
            case JCTree.JCContinue jcContinue -> translateContinue(jcContinue);
            case JCTree.JCBreak jcBreak -> translateBreak(jcBreak);
            case JCTree.JCSwitch jcSwitch -> translateSwitchStatement(jcSwitch, expressionContext);
            case JCTree.JCSkip _ -> List.of();
            default -> {
                reporter.reportError(statement, "notSupported", "statement " + statement.getClass().getSimpleName());
                yield List.of();
            }
        };
        
        statements.addAll(rhsStatements);
        return statements;
    }
    
    private List<Statement> translateBreak(JCTree.JCBreak jcBreak) {
        var origin = reporter.toOrigin(jcBreak);
        Statement result;
        if (jcBreak.label == null) {
            result = new BreakOrContinueStmt(origin, null, null, 1, false);
        } else {
            var targetLabel = reporter.getName(jcBreak, jcBreak.label);
            result = new BreakOrContinueStmt(origin, null, targetLabel, 0, false);
        }
        return List.of(result);
    }

    public List<Statement> translateContinue(JCTree.JCContinue jcContinue) {
        var origin = reporter.toOrigin(jcContinue);
        if (jcContinue.label == null) {
            return List.of(new BreakOrContinueStmt(origin, null, null, 1, true));
        } else {
            var targetLabel = reporter.getName(jcContinue, jcContinue.label);
            return List.of(new BreakOrContinueStmt(origin, null, targetLabel, 0, true));
        }
    }

    private List<Statement> translateReturn(JCTree.JCReturn returnStatement, ExpressionContext expressionContext) {
        var origin = reporter.toOrigin(returnStatement);
        var expr = returnStatement.getExpression();
        if (expr == null) {
            return List.of(new ReturnStmt(origin, null, null));
        } else {
            var returnExpr = expressionCompiler.toAssignmentRhs(expr, expressionContext.forbidImpure());
            return List.of(new ReturnStmt(origin, null, List.of(returnExpr)));
        }
    }

    private List<Statement> translateIfStatement(JCTree.JCIf ifStatement, ExpressionContext expressionContext) {
        var origin = reporter.toOrigin(ifStatement);
        var conditionWithFlows = expressionCompiler.toExprWithFlows(ifStatement.getCondition(), expressionContext);
        var thenBranch = blockifyStatements(origin, translateStatement(ifStatement.getThenStatement()));
        BlockStmt elseBranch = null;
        if (ifStatement.getElseStatement() != null) {
            elseBranch = blockifyStatements(origin, translateStatement(ifStatement.getElseStatement()));
        }
        var flowStatements = new ArrayList<Statement>();
        for(var flowCast : conditionWithFlows.flows()) {
            Type translatedType = flowCast.type();
            LocalVariable localVariable = new LocalVariable(origin, flowCast.name(), translatedType, false);

            var rhs = new ExprRhs(origin, null, flowCast.expression());
            List<Expression> lhss = List.of(new IdentifierExpr(localVariable.getOrigin(), localVariable.getName()));
            List<AssignmentRhs> rhss = List.of(rhs);
            var dafnyInitializer = new AssignStatement(origin, null, lhss, rhss, false);

            var decl = new VarDeclStmt(origin, null, List.of(localVariable), dafnyInitializer);
            flowStatements.add(decl);
        }
        if (!flowStatements.isEmpty()) {
            flowStatements.addAll(thenBranch.getBody());
            thenBranch = new BlockStmt(thenBranch.getOrigin(), thenBranch.getAttributes(), thenBranch.getLabels(), flowStatements);
        }
        return List.of(new IfStmt(origin, null, List.of(), false, conditionWithFlows.expression(),
                thenBranch, elseBranch));
    }

    private List<Statement> translateVariableDeclaration(IOrigin origin, JCTree.JCVariableDecl variableDecl, ExpressionContext expressionContext) {
        Type translatedType = generator.translateType(variableDecl.getType().type, origin, variableDecl.getModifiers());
        LocalVariable localVariable = new LocalVariable(origin, nameCompiler.getCompiledName(variableDecl.sym, variableDecl),
                translatedType, false);
        ConcreteAssignStatement dafnyInitializer = null;
        if (variableDecl.getInitializer() != null) {
            var rhs = expressionCompiler.toAssignmentRhs(variableDecl.getInitializer(), expressionContext);
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
                                   java.util.function.Function<List<Statement>, List<Statement>> transformBody, 
                                   ExpressionContext expressionContext) {
        var origin = reporter.toOrigin(loop);
        var header = new MethodOrLoopContract(loop, false);
        var postHeader = methodOrLoopContractCompiler.extractContract(baseGenerator, (JCTree.JCBlock) body, header);

        checkLoopHeaderAndSetupLabels(loop, labels, header);

        var dafnyCondition = expressionCompiler.toExpr(condition, expressionContext);
        var bodyStatements = translateStatements(postHeader);
        var newBodyStatements = transformBody.apply(bodyStatements);
        return new WhileStmt(origin, null, labels, header.loopInvariants, new Specification<>(header.decreases, null),
                new Specification<>(header.modifies, null), new BlockStmt(origin, null, List.of(), newBodyStatements),
                dafnyCondition);
    }

    private void checkLoopHeaderAndSetupLabels(JCTree.JCStatement loop, List<Label> labels, MethodOrLoopContract header) {
        checkEmptyExpressions(loop, header.preconditions, "preconditions", "loop");
        checkEmptyExpressions(loop, header.postconditions, "postconditions", "loop");

        outerLoop = loop;
        for (var label : labels) {
            labelToLoop.put(label.getName(), loop);
        }
    }

    private List<Statement> translateExpressionStatement(JCTree.JCExpressionStatement statement, IOrigin originOverride, ExpressionContext expressionContext) {
        var expr = statement.getExpression();
        if (expr instanceof JCTree.JCMethodInvocation invocation) {
            return translateStatementMethodInvocation(invocation, expressionContext);
        }
        generator.toExprWithFlows(expr, originOverride, expressionContext);
        return List.of();
    }

    public List<Statement> translateStatementMethodInvocation(JCTree.JCMethodInvocation invocation, ExpressionContext expressionContext) {
        var jverifyMethod = BaseDafnyGenerator.getJVerifyMethod(invocation);
        if (jverifyMethod != null) {
            return translateJVerifyMethodInvocation(invocation, jverifyMethod, expressionContext);
        } else {
            return translateVanillaJavaMethodInvocation(invocation, expressionContext);
        }
    }

    private List<Statement> translateJVerifyMethodInvocation(JCTree.JCMethodInvocation invocation, 
                                                             Symbol.MethodSymbol jverifyMethod, ExpressionContext expressionContext) {
        var name = jverifyMethod.getQualifiedName().toString();
        if (name.equals("check")) {
            if (invocation.args.size() != 1) {
                throw new JavaViolationException("Check should have a single argument");
            }
            return List.of(new AssertStmt(reporter.toOrigin(invocation), null,
                    expressionCompiler.toExpr(invocation.args.getFirst(), expressionContext), null));
        } else {
            if (JVerifyUtils.isConstructor(methodSymbol)) {
                reporter.reportError(invocation, "contractForConstructor");
            } else {
                reporter.reportError(invocation, "contractAfterBody");
            }
            return List.of();
        }
    }

    private List<Statement> translateVanillaJavaMethodInvocation(JCTree.JCMethodInvocation invocation, ExpressionContext expressionContext) {
        var origin = reporter.toOrigin(invocation);
        var superIdent = getSuperIdent(invocation);
        com.sun.tools.javac.util.List<JCTree.JCExpression> javaArguments = invocation.getArguments();
        if (superIdent != null) {
            Symbol.MethodSymbol baseConstructor = (Symbol.MethodSymbol) superIdent.sym;

            if (!baseGenerator.symbolsWithAContract.contains(baseConstructor)) {
                return List.of();
            }

            var baseConstructorName = nameCompiler.getCompiledName(baseConstructor, superIdent);
            var baseConstructorClassName = nameCompiler.getCompiledName(baseConstructor.enclClass(), superIdent);
            var initName = nameCompiler.getInitMethodName(baseConstructorClassName, baseConstructorName);
            NameSegment callee = new NameSegment(origin, initName, null);
            var applySuffix = expressionCompiler.createCall(origin, callee, javaArguments.stream(), expressionContext);
            var initCall = new AssignStatement(origin, null, List.of(),
                    List.of(new ExprRhs(applySuffix.getOrigin(), null, applySuffix)), false);

            return List.of(initCall);
        }

        var expr = generator.toExpr(invocation, null, expressionContext);
        return List.of(new AssignStatement(origin, null, List.of(),
                List.of(new ExprRhs(expr.getOrigin(), null, expr)), false));
    }

    public static JCTree.JCIdent getSuperIdent(JCTree.JCMethodInvocation invocation) {
        return invocation.getMethodSelect() instanceof JCTree.JCIdent ident && ident.name == ident.name.table.names._super ? ident : null;
    }

    public List<Statement> translateSwitchStatement(JCTree.JCSwitch switchStmt, ExpressionContext expressionContext) {
        var origin = reporter.toOrigin(switchStmt);
        var patternBodies = new Patterns(baseGenerator).translateSwitchLabels(switchStmt, expressionContext);
        if (patternBodies == null) {
            return List.of();
        }

        var translatedCases = patternBodies.stream().map(patternBody -> {
            var caseOrigin = reporter.toOrigin(patternBody.cas());
            var body = patternBody.body();

            // A switch rule introduces either an expression, a block, or a throw statement.
            // Within a switch statement, a switch rule expression must be a statement expression.
            List<Statement> translatedBody = switch (body) {
                case null ->
                        List.of(); // This only happens for statement labels, which would have already raised an error in translateSwitchLabels
                case JCTree.JCExpressionStatement bodyStatement -> translateStatement(bodyStatement);
                case JCTree.JCBlock bodyBlock -> translateStatement(bodyBlock);
                case JCTree.JCThrow ignored -> {
                    reporter.reportError(body, "notSupported", "switch rule throw");
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

        var source = expressionCompiler.toExpr(switchStmt.getExpression(), expressionContext);
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
            reporter.reportError(tree, "wrongContract", typeName, containerName);
        }
    }

    /**
     * Returns the specified statement as-is if it's already a {@link BlockStmt},
     * or wraps it in a singleton block otherwise.
     */
    public static BlockStmt blockifyStatements(IOrigin origin, List<Statement> statements) {
        if (statements.isEmpty()) {
            return new BlockStmt(origin, null, List.of(), statements);
        }
        return statements.getFirst() instanceof BlockStmt block
                ? block
                : new BlockStmt(origin, null, List.of(), statements);
    }
}

class Static1 {
    static int y = Static2.x; 
}

class Static2 {
    static int x = Static1.y;
}
