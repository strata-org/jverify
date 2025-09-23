package com.aws.jverify.verifier.compiler.dafnygenerator.base;

import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.aws.jverify.verifier.compiler.simplifications.MethodOrLoopContract;
import com.aws.jverify.verifier.compiler.dafnygenerator.*;
import com.aws.jverify.verifier.compiler.simplifications.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;

import java.util.*;
import java.util.stream.Collectors;

public class BlockCompiler {

    public final BaseDafnyGenerator generator;
    private final ExpressionCompiler expressionCompiler;
    MethodOrLoopContractCompiler methodOrLoopContractCompiler;
    private final Symbol.MethodSymbol methodSymbol;
    private final List<StatementCompiler> statementCompilers = new ArrayList<>();

    public BlockCompiler(BaseDafnyGenerator compiler, Symbol.MethodSymbol methodSymbol) {
        this.generator = compiler;
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
        var origin = Objects.requireNonNullElseGet(originOverride, () -> generator.toOrigin(statement));
        if (statement instanceof JCTree.JCLabeledStatement labeledStatement) {
            labels.add(new Label(origin, labeledStatement.getLabel().toString()));
            return translateStatement(labeledStatement.getStatement());
        }
        var labels = this.labels.stream().toList();
        this.labels.clear();


        List<Statement> statements = new ArrayList<>();
        var expressionContext = new ExpressionContext(statements::add, true, this);
        
        for (var compiler : statementCompilers) {
            var result = compiler.compile(statement, labels, expressionContext);
            if (result != null) {
                return result;
            }
        }

        List<Statement> rhsStatements = switch (statement) {
            case JCTree.JCExpressionStatement expressionStatement -> {
                yield translateExpressionStatement(expressionStatement, originOverride, expressionContext);
            }
            case JCTree.JCAssert assertStmt -> {
                yield List.of(new AssertStmt(origin, null, expressionCompiler.toExpr(assertStmt.getCondition(), expressionContext), null));
            }
            case JCTree.JCIf ifStatement -> {
                yield translateIfStatement(ifStatement, expressionContext);
            }
            case JCTree.JCBlock blockStatement -> {
                yield List.of(new BlockStmt(origin, null, List.of(),
                        translateStatements(blockStatement.getStatements())));
            }
            case JCTree.JCReturn returnStatement -> {
                yield translateReturn(returnStatement, expressionContext);
            }
            case JCTree.JCVariableDecl variableDecl -> {
                yield translateVariableDeclaration(origin, variableDecl, expressionContext);
            }
            case JCTree.JCWhileLoop whileLoop -> {
                yield List.of(translateLoop(whileLoop, whileLoop.getCondition(), whileLoop.body, labels, x -> x, expressionContext));
            }
            case JCTree.JCContinue jcContinue -> {
                yield translateContinue(jcContinue);
            }
            case JCTree.JCBreak jcBreak -> {
                yield translateBreak(jcBreak);
            }
            case JCTree.JCSwitch jcSwitch -> {
                yield translateSwitchStatement(jcSwitch, expressionContext);
            }
            case JCTree.JCSkip _ -> {
                yield List.of();
            }
            default -> {
                generator.reportError(statement, "notSupported", "statement " + statement.getClass().getSimpleName());
                yield List.of();
            }
        };
        
        statements.addAll(rhsStatements);
        return statements;
    }

    private List<Statement> translateBreak(JCTree.JCBreak jcBreak) {
        var origin = generator.toOrigin(jcBreak);
        Statement result;
        if (jcBreak.label == null) {
            result = new BreakOrContinueStmt(origin, null, null, 1, false);
        } else {
            var targetLabel = generator.getName(jcBreak, jcBreak.label);
            result = new BreakOrContinueStmt(origin, null, targetLabel, 0, false);
        }
        return List.of(result);
    }

    public List<Statement> translateContinue(JCTree.JCContinue jcContinue) {
        var origin = generator.toOrigin(jcContinue);
        if (jcContinue.label == null) {
            return List.of(new BreakOrContinueStmt(origin, null, null, 1, true));
        } else {
            var targetLabel = generator.getName(jcContinue, jcContinue.label);
            return List.of(new BreakOrContinueStmt(origin, null, targetLabel, 0, true));
        }
    }

    private List<Statement> translateReturn(JCTree.JCReturn returnStatement, ExpressionContext expressionContext) {
        var origin = generator.toOrigin(returnStatement);
        var expr = returnStatement.getExpression();
        if (expr == null) {
            return List.of(new ReturnStmt(origin, null, null));
        } else {
            var returnExpr = expressionCompiler.toAssignmentRhs(expr, expressionContext.forbidImpure());
            return List.of(new ReturnStmt(origin, null, List.of(returnExpr)));
        }
    }

    private List<Statement> translateIfStatement(JCTree.JCIf ifStatement, ExpressionContext expressionContext) {
        var origin = generator.toOrigin(ifStatement);
        var condition = expressionCompiler.toExpr(ifStatement.getCondition(), expressionContext);
        var thenBranch = blockifyStatements(origin, translateStatement(ifStatement.getThenStatement()));
        BlockStmt elseBranch = null;
        if (ifStatement.getElseStatement() != null) {
            elseBranch = blockifyStatements(origin, translateStatement(ifStatement.getElseStatement()));
        }
        return List.of(new IfStmt(origin, null, List.of(), false, condition,
                thenBranch, elseBranch));
    }

    private List<Statement> translateVariableDeclaration(IOrigin origin, JCTree.JCVariableDecl variableDecl, ExpressionContext expressionContext) {
        Type translatedType = generator.getFinalGenerator().translateType(variableDecl.getType().type, origin, variableDecl.getModifiers());
        LocalVariable localVariable = new LocalVariable(origin, generator.nameCompiler.getCompiledName(variableDecl.sym, variableDecl),
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
        var origin = generator.toOrigin(loop);
        var header = new MethodOrLoopContract(loop, false);
        var postHeader = methodOrLoopContractCompiler.extractContract(generator, (JCTree.JCBlock) body, header);

        checkLoopHeaderAndSetupLabels(loop, labels, header);

        var dafnyCondition = expressionCompiler.toExpr(condition, expressionContext);
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
        for (var label : labels) {
            labelToLoop.put(label.getName(), loop);
        }
    }

    private List<Statement> translateExpressionStatement(JCTree.JCExpressionStatement statement, IOrigin originOverride, ExpressionContext expressionContext) {
        var expr = statement.getExpression();
        if (expr instanceof JCTree.JCMethodInvocation invocation) {
            return translateStatementMethodInvocation(invocation, expressionContext);
        }
        expressionCompiler.toExpr(expr, originOverride, expressionContext);
        return List.of();
    }

    private List<Statement> translateStatementMethodInvocation(JCTree.JCMethodInvocation invocation, ExpressionContext expressionContext) {
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
            return List.of(new AssertStmt(generator.toOrigin(invocation), null,
                    expressionCompiler.toExpr(invocation.args.getFirst(), expressionContext), null));
        } else {
            if (BaseDafnyGenerator.isConstructor(methodSymbol)) {
                generator.reportError(invocation, "contractForConstructor");
            } else {
                generator.reportError(invocation, "contractAfterBody");
            }
            return List.of();
        }
    }

    private List<Statement> translateVanillaJavaMethodInvocation(JCTree.JCMethodInvocation invocation, ExpressionContext expressionContext) {
        var origin = generator.toOrigin(invocation);
        var superIdent = getSuperIdent(invocation);
        com.sun.tools.javac.util.List<JCTree.JCExpression> javaArguments = invocation.getArguments();
        if (superIdent != null) {
            Symbol.MethodSymbol baseConstructor = (Symbol.MethodSymbol) superIdent.sym;

            if (!generator.symbolsWithAContract.contains(baseConstructor)) {
                return List.of();
            }

            var baseConstructorName = generator.nameCompiler.getCompiledName(baseConstructor, superIdent);
            var baseConstructorClassName = generator.nameCompiler.getCompiledName(baseConstructor.enclClass(), superIdent);
            var initName = generator.nameCompiler.getInitMethodName(baseConstructorClassName, baseConstructorName);
            NameSegment callee = new NameSegment(origin, initName, null);
            var applySuffix = expressionCompiler.createCall(origin, callee, javaArguments.stream(), expressionContext);
            var initCall = new AssignStatement(origin, null, List.of(),
                    List.of(new ExprRhs(applySuffix.getOrigin(), null, applySuffix)), false);

            return List.of(initCall);
        }
        Expression callee = expressionCompiler.toExpr(invocation.getMethodSelect(), expressionContext);

        var arguments = javaArguments.stream().map(e -> expressionCompiler.toExpr(e, expressionContext));
        ApplySuffix applySuffix = ExpressionCompiler.createCall2(origin, callee, arguments);
        return List.of(new AssignStatement(origin, null, List.of(),
                List.of(new ExprRhs(applySuffix.getOrigin(), null, applySuffix)), false));
    }

    public static JCTree.JCIdent getSuperIdent(JCTree.JCMethodInvocation invocation) {
        return invocation.getMethodSelect() instanceof JCTree.JCIdent ident && ident.name == ident.name.table.names._super ? ident : null;
    }

    public List<Statement> translateSwitchStatement(JCTree.JCSwitch switchStmt, ExpressionContext expressionContext) {
        var origin = generator.toOrigin(switchStmt);
        var patternBodies = new Patterns(generator).translateSwitchLabels(switchStmt, expressionContext);
        if (patternBodies == null) {
            return List.of();
        }

        var translatedCases = patternBodies.stream().map(patternBody -> {
            var caseOrigin = generator.toOrigin(patternBody.cas());
            var body = patternBody.body();

            // A switch rule introduces either an expression, a block, or a throw statement.
            // Within a switch statement, a switch rule expression must be a statement expression.
            List<Statement> translatedBody = switch (body) {
                case null ->
                        List.of(); // This only happens for statement labels, which would have already raised an error in translateSwitchLabels
                case JCTree.JCExpressionStatement bodyStatement -> translateStatement(bodyStatement);
                case JCTree.JCBlock bodyBlock -> translateStatement(bodyBlock);
                case JCTree.JCThrow ignored -> {
                    generator.reportError(body, "notSupported", "switch rule throw");
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
            generator.reportError(tree, "wrongContract", typeName, containerName);
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
