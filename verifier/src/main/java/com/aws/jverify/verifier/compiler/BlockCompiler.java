package com.aws.jverify.verifier.compiler;

import com.aws.jverify.Pure;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.simplifications.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;

import java.util.*;
import java.util.stream.Collectors;

import static com.aws.jverify.verifier.compiler.JavaToDafnyCompiler.*;

public class BlockCompiler {

    public final JavaToDafnyCompiler compiler;
    private final Symbol.MethodSymbol methodSymbol;
    private final List<StatementCompiler> statementCompilers = new ArrayList<>();

    public BlockCompiler(JavaToDafnyCompiler compiler, Symbol.MethodSymbol methodSymbol) {
        this.compiler = compiler;
        this.methodSymbol = methodSymbol;
        statementCompilers.add(new ForLoopCompiler(this));
        statementCompilers.add(new DoWhileLoopCompiler(this));
        statementCompilers.add(new ImpureExpressionStatementCompiler(this));
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

        for(var compiler : statementCompilers) {
            var result = compiler.compile(statement, labels);
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
        Statement result;
        if (jcBreak.label == null) {
            result = new BreakOrContinueStmt(origin, null, null, 1, false);
        } else {
            var targetLabel = compiler.getName(jcBreak, jcBreak.label);
            result = new BreakOrContinueStmt(origin, null, targetLabel, 0, false);
        }
        return List.of(result);
    }

    public List<Statement> translateContinue(JCTree.JCContinue jcContinue) {
        var origin = compiler.toOrigin(jcContinue);
        if (jcContinue.label == null) {
            return List.of(new BreakOrContinueStmt(origin, null, null, 1, true));
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
            var newLocalVarExpr = new NameSegment(exprOrigin, compiler.nameCompiler.RETURN_VARIABLE_NAME, null);
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
        LocalVariable localVariable = new LocalVariable(origin, compiler.nameCompiler.getCompiledName(variableDecl.sym),
                compiler.translateType(variableDecl.getType().type, origin, variableDecl.getModifiers()), false);
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
        var postHeader = new ContractCompiler(compiler).translateHeader(body, header, false, true);

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
            if (JavaToDafnyCompiler.isConstructor(methodSymbol)) {
                compiler.reportError(invocation, "contractForConstructor");
            } else {
                compiler.reportError(invocation, "contractAfterBody");
            }
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
                case null -> List.of(); // This only happens for statement labels, which would have already raised an error in translateSwitchLabels
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
     * Returns the specified statement as-is if it's already a {@link BlockStmt},
     * or wraps it in a singleton block otherwise.
     */
    public static BlockStmt blockifyStatements(IOrigin origin,  List<Statement> statements) {
        if (statements.isEmpty()) {
            return new BlockStmt(origin, null, List.of(), statements);
        }
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
                Symtab symtab = Symtab.instance(compiler.context);
                Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) TreeInfo.symbol(newClass.clazz);
                if (classSymbol.type != symtab.objectType && compiler.isValueType(classSymbol)) {
                    var datatypeValue = ValueTypeCompiler.translateNewRecord(compiler.expressionCompiler, origin, newClass);
                    return new ExprRhs(origin, null, datatypeValue);
                }
                NameSegment classBaseType = new ModifiableObjectCompiler(compiler).getNewClassType(newClass);

                String ctorNameStr = compiler.nameCompiler.getCompiledName(newClass.constructor);
                Name ctorName = new Name(origin, ctorNameStr);
                var ty = new UserDefinedType(origin, new ExprDotName(origin, classBaseType, ctorName,null));

                var argBindings = newClass.getArguments().stream()
                        .map(a -> new ActualBinding(
                                null, compiler.expressionCompiler.toExpr(a), false))
                        .toList();
                return new AllocateClass(origin, null, ty, new ActualBindings(argBindings));
            }
            case JCTree.JCNewArray newArray -> {
                var arrayDimensions = newArray.getDimensions().stream().map(compiler.expressionCompiler::toExpr).toList();
                var arrayInitializers = newArray.getInitializers();
                var arrayJavaType = ((com.sun.tools.javac.code.Type.ArrayType) newArray.type).elemtype;
                if (arrayJavaType instanceof com.sun.tools.javac.code.Type.ArrayType) {
                    compiler.reportError(expr, "notSupported", "multi-dimensional arrays");
                }
                var arrayDafnyType = compiler.translateType(arrayJavaType, compiler.toOrigin(newArray), null);

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

    public MethodOrLoopContract extractContract(JCTree.JCMethodDecl methodDecl, boolean reportErrors) {
        var methodAnnotationsByName = JavaToDafnyCompiler.getAnnotationsByName(methodDecl.getModifiers());

        var isPure = methodAnnotationsByName.containsKey(Pure.class.getName());
        var header = new MethodOrLoopContract(methodDecl, isPure);
        if (methodDecl.getBody() != null) {
            var allowFooter = isConstructor(methodDecl.sym);
            new ContractCompiler(compiler).
                    translateHeader(methodDecl.getBody(), header, allowFooter, reportErrors);
        }

        return header;
    }
}
