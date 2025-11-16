package com.aws.jverify.verifier.compiler.dafnygenerator.base;

import com.aws.jverify.Impure;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.dafnygenerator.DafnyGenerator;
import com.aws.jverify.verifier.compiler.dafnygenerator.NullableGenerator;
import com.aws.jverify.verifier.compiler.simplifications.JVerifyUtils;
import com.aws.jverify.verifier.compiler.simplifications.NameCompiler;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Context;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ExpressionCompiler {
    public final Reporter reporter;
    public final BaseDafnyGenerator baseGenerator;
    public final DafnyGenerator generator;
    private final NameCompiler nameCompiler;
    private final JVerifyUtils utils;

    public ExpressionCompiler(Context context, BaseDafnyGenerator baseGenerator) {
        this.baseGenerator = baseGenerator;
        generator = context.get(DafnyGenerator.class);
        reporter = Reporter.instance(context);
        utils = JVerifyUtils.instance(context);
        nameCompiler = NameCompiler.instance(context);
    }

    public ExpressionWithFlows toExprWithFlows(JCTree tree, ExpressionContext context) {
        if (tree instanceof JCTree.JCExpression expression) {
            return toExprWithFlows(expression, context);
        }
        reporter.reportError(tree, "notSupported", tree.getClass().getSimpleName() + " as an expression");
        return new ExpressionWithFlows(JVerifyUtils.getHole(reporter.toOrigin(tree)));
    }

    @Nullable
    BinaryExprOpcode toDafny(Symbol.OperatorSymbol operator) {
        return switch (operator.name.toString()) {
            case "-" -> BinaryExprOpcode.Sub;
            case "+" -> BinaryExprOpcode.Add;
            case "*" -> BinaryExprOpcode.Mul;
            case "/" -> BinaryExprOpcode.Div;
            case "==" -> BinaryExprOpcode.Eq;
            case "!=" -> BinaryExprOpcode.Neq;
            case "<" -> BinaryExprOpcode.Lt;
            case "<=" -> BinaryExprOpcode.Le;
            case ">" -> BinaryExprOpcode.Gt;
            case ">=" -> BinaryExprOpcode.Ge;
            case "||" -> BinaryExprOpcode.Or;
            case "&&" -> BinaryExprOpcode.And;
            case "%" -> BinaryExprOpcode.Mod;
            case "&" -> BinaryExprOpcode.BitwiseAnd;
            case "|" -> BinaryExprOpcode.BitwiseOr;
            case "^" -> BinaryExprOpcode.BitwiseXor;
            default -> null;
        };
    }

    private Expression translateSwitchExpression(JCTree.JCSwitchExpression switchExpr, IOrigin origin,  ExpressionContext context) {
        var newContext = context.forbidImpure();
        var patternBodies = new Patterns(baseGenerator).translateSwitchLabels(switchExpr, context);
        if (patternBodies == null) {
            return JVerifyUtils.getHole(origin);
        }

        var translatedCases = patternBodies.stream().map(patternBody -> {
            var caseOrigin = reporter.toOrigin(patternBody.cas());
            var body = patternBody.body();
            final Expression translatedBody;

            // A switch rule introduces either an expression, a block, or a throw statement.
            if (body == null) {
                // This only happens for statement labels, which would have already raised an error in translateSwitchLabels
                translatedBody = JVerifyUtils.getHole(origin);
            } else if (body instanceof JCTree.JCExpression expr) {
                translatedBody = toExpr(expr, newContext.withExpectedType(switchExpr.type));
            } else {
                var bodyKind = body instanceof JCTree.JCBlock ? "block" : "throw statement";
                reporter.reportError(body, "notSupported", "switch rule %s".formatted(bodyKind));
                translatedBody = JVerifyUtils.getHole(caseOrigin);
            }
            return new NestedMatchCaseExpr(caseOrigin, patternBody.pattern(), translatedBody, null);
        }).toList();

        var source = toExpr(switchExpr.getExpression(), context);
        return new NestedMatchExpr(origin, source, translatedCases, true, null);
    }

    public Expression toExprWithFlows(List<JCTree.JCStatement> statements, ExpressionContext context) {
        var last = statements.getLast();
        var result = toExpr(last, context);
        for(int index = statements.size() - 2; index >= 0; index--) {
            var statement = statements.get(index);
            var origin = reporter.toOrigin(statement);
            if (statement instanceof JCTree.JCVariableDecl variableDecl) {
                if (variableDecl.init == null) {
                    reporter.reportError(statement, "pureAssignmentNeedsInitializer", variableDecl.name.toString());
                    return result;
                }
                var type = baseGenerator.translateType(variableDecl.type, reporter.toOrigin(variableDecl));
                String name = baseGenerator.nameCompiler.getCompiledName(variableDecl.sym, variableDecl);
                var returnVar = new BoundVar(origin, new Name(origin, name), type, false);
                var lhs = new CasePattern<>(origin, name, returnVar, null);
                result = new LetExpr(origin, List.of(lhs), List.of(toExpr(variableDecl.init, context)), result, true, null);
            } else if (statement instanceof JCTree.JCIf ifStatement && ifStatement.getElseStatement() == null) {
                ExpressionWithFlows conditionWithFlows = toExprWithFlows(ifStatement.getCondition(), context);
                var thenExpression = toExpr(ifStatement.getThenStatement(), context);
                thenExpression = applyFlowCastsToExpr(thenExpression, conditionWithFlows.flows());
                result = new ITEExpr(reporter.toOrigin(ifStatement), false,
                        conditionWithFlows.expression(), thenExpression, result);
            } else {
                if (statement instanceof JCTree.JCExpressionStatement expressionStatement &&
                        expressionStatement.getExpression() instanceof JCTree.JCMethodInvocation invocation) {
                    var jverifyMethod = BaseDafnyGenerator.getJVerifyMethod(invocation);
                    if (jverifyMethod != null) {
                        var dafnyStatement = getJVerifyStatement(invocation, jverifyMethod, context);
                        result = new StmtExpr(origin, dafnyStatement, result);
                        continue;
                    }
                }
                reporter.reportError(statement, "pureBlockNotLastMustBeVariableDeclaration");
                result = JVerifyUtils.getHole(origin);
            }
        }
        return result;
    }

    @Nullable
    public Statement getJVerifyStatement(JCTree.JCMethodInvocation invocation,
                                         Symbol.MethodSymbol jverifyMethod,
                                         ExpressionContext expressionContext) {

        var name = jverifyMethod.getQualifiedName().toString();
        if (name.equals("check")) {
            if (invocation.args.size() != 1) {
                throw new JavaViolationException("Check should have a single argument");
            }
            return new AssertStmt(reporter.toOrigin(invocation), null,
                    toExpr(invocation.args.getFirst(), expressionContext), null);
        } if (name.equals("assume")) {
            if (invocation.args.size() != 1) {
                throw new JavaViolationException("Check should have a single argument");
            }
            return new AssumeStmt(reporter.toOrigin(invocation), null,
                    toExpr(invocation.args.getFirst(), expressionContext));
        }
        return null;
    }

    private Expression toExpr(JCTree.JCStatement statement, ExpressionContext context) {
        IOrigin origin = reporter.toOrigin(statement);
        context = context.forbidImpure();
        return switch (statement) {
            case JCTree.JCBlock block -> {
                if (block.getStatements().isEmpty()) {
                    reporter.reportError(block, "pureMethodLastStatement");
                    yield JVerifyUtils.getHole(origin);
                } else {
                    yield toExprWithFlows(block.getStatements(), context);
                }
            }
            case JCTree.JCIf ifStatement -> {
                if (ifStatement.getElseStatement() == null) {
                    reporter.reportError(statement, "pureMethodEndingIfThen");
                    yield JVerifyUtils.getHole(origin);
                }
                ExpressionWithFlows conditionWithFlows = toExprWithFlows(ifStatement.getCondition(), context);
                yield new ITEExpr(origin, false,
                        conditionWithFlows.expression(),
                    applyFlowCastsToExpr(toExpr(ifStatement.getThenStatement(), context), conditionWithFlows.flows()),
                    toExpr(ifStatement.getElseStatement(), context));
            }
            case JCTree.JCReturn returnStatement -> toExpr(returnStatement.expr, context.withExpectedType(returnStatement.type));
            default -> {
                reporter.reportError(statement, "pureMethodLastStatement");
                yield JVerifyUtils.getHole(origin);
            }
        };
    }

    public Expression toExpr(JCTree tree, ExpressionContext context) {
        if (tree instanceof JCTree.JCExpression expression) {
            return generator.toExprWithFlows(expression, null, context).expression();
        }
        reporter.reportError(tree, "notSupported", tree.getClass().getSimpleName() + " as an expression");
        return JVerifyUtils.getHole(reporter.toOrigin(tree));
    }

    public Expression toExpr(JCTree.JCExpression expr, ExpressionContext context) {
        return generator.toExprWithFlows(expr, null, context).expression();
    }

    public ExpressionWithFlows toExprWithFlows(JCTree.JCExpression expr, ExpressionContext context) {
        return generator.toExprWithFlows(expr, null, context);
    }

    public ExpressionWithFlows toExprBase(JCTree.JCExpression expr, IOrigin originOverride, ExpressionContext context) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> reporter.toOrigin(expr));
        switch (expr) {
            case JCTree.JCConditional conditional -> {
                return new ExpressionWithFlows(translateConditional(conditional, origin, context));
            }
            case JCTree.JCSwitchExpression switchExpr -> {
                return new ExpressionWithFlows(translateSwitchExpression(switchExpr, origin, context));
            }
            case JCTree.JCUnary unary -> {
                return new ExpressionWithFlows(translateUnary(expr, unary, origin, context));
            }
            case JCTree.JCBinary binary -> {
                return translateBinary(binary, context);
            }
            case JCTree.JCIdent identifier -> {
                return new ExpressionWithFlows(translateIdentifier(identifier, origin));
            }
            case JCTree.JCLiteral literal -> {
                return new ExpressionWithFlows(translateLiteral(literal, origin));
            }
            case JCTree.JCMethodInvocation invocation -> {
                return new ExpressionWithFlows(translateMethodInvocation(invocation, origin, context));
            }
            case JCTree.JCFieldAccess fieldAccess -> {
                return new ExpressionWithFlows(translateFieldAccess(fieldAccess, origin, context));
            }
            case JCTree.JCArrayAccess arrayAccess -> {
                return new ExpressionWithFlows(translateArrayAccess(arrayAccess, origin));
            }
            case JCTree.JCParens parens -> {
                return toExprWithFlows(parens.getExpression(), context);
            }
            case JCTree.JCAssign assign -> {
                return new ExpressionWithFlows(translateAssign(assign, origin, context));
            }
            case JCTree.JCAssignOp assignOp -> {
                if (context.statementWriter() == null) {
                    reporter.reportError(expr, "mutatingExpression", assignOp.getOperator().name.toString() + "=");
                    return new ExpressionWithFlows(JVerifyUtils.getHole(origin));
                } else {
                    return new ExpressionWithFlows(translateAssignOp(assignOp, origin, context));
                }
            }
            case JCTree.JCInstanceOf instanceOf -> {
                return translateInstanceOf(instanceOf, origin, context);
            }
            case JCTree.JCTypeCast cast -> {
                return new ExpressionWithFlows(translateCast(cast, origin, context));
            }
            case JCTree.JCLambda _ ->
                throw new RuntimeException("Lambdas should have been rewritten, but found one at " + origin);
            case JCTree.JCMemberReference _ ->
                throw new RuntimeException("Member references should have been rewritten, but found one at " + origin);
            case JCTree.JCTypeApply typeApply -> {
                return new ExpressionWithFlows(translateTypeApplication(typeApply, origin, context));
            }
            case JCTree.JCNewClass newClass -> {
                return new ExpressionWithFlows(translateNew(expr, newClass, origin, context));
            }
            default -> { }
        }
        reporter.reportError(expr, "notSupported", expr.getClass().getSimpleName() + " in an expression");
        return new ExpressionWithFlows(JVerifyUtils.getHole(origin));
    }

    private Expression translateAssign(JCTree.JCAssign assign, IOrigin origin, ExpressionContext context) {
        if (context.statementWriter() != null) {
            Expression target = toExpr(assign.getVariable(), context);
            List<Expression> lhss = List.of(target);
            ExpressionContext rhsContext = context.withExpectedType(NullableGenerator.getNullableType(assign.getVariable()));
            List<AssignmentRhs> rhss = List.of(toAssignmentRhs(assign.getExpression(), rhsContext));
            context.statementWriter().accept(new AssignStatement(origin, null, lhss, rhss, false));
            return target;
        } else {
            reporter.reportError(assign, "notSupported", assign.getClass().getSimpleName() + " in a pure expression");
            return JVerifyUtils.getHole(origin);
        }
    }

    public AssignmentRhs toAssignmentRhs(JCTree.JCExpression expr, ExpressionContext expressionContext) {
        return toAssignmentRhs(expr, null, expressionContext);
    }

    public AssignmentRhs toAssignmentRhs(JCTree.JCExpression expr, IOrigin originOverride, ExpressionContext expressionContext) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> reporter.toOrigin(expr));
        switch (expr) {
            case JCTree.JCNewClass newClass -> {
                return generator.translateNewClassToAssignmentRhs(newClass, origin, expressionContext);
            }
            case JCTree.JCNewArray _ -> {
                throw new RuntimeException("not supported. should have already been lowered");
            }
            default -> {
            }
        }
        var dafnyExpr = generator.toExpr(expr, origin, expressionContext);
        return new ExprRhs(origin, null, dafnyExpr);
    }

    private Expression translateAssignOp(JCTree.JCAssignOp assignOp, IOrigin origin, ExpressionContext context) {
        Expression target = toExpr(assignOp.getVariable(), context);
        List<Expression> lhss = List.of(target);
        var operated = translateBinary(
                assignOp, assignOp.getVariable().type, null,
                assignOp.getOperator(), target, toExpr(assignOp.getExpression(), context));
        List<AssignmentRhs> rhss = List.of(new ExprRhs(origin, null, operated));
        context.statementWriter().accept(new AssignStatement(origin, null, lhss, rhss, false));
        return target;
    }

    private Expression translateNew(JCTree.JCExpression expr, JCTree.JCNewClass newClass, IOrigin origin,
                                    ExpressionContext context) {
        Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) newClass.type.tsym;
        Symtab symtab = Symtab.instance(baseGenerator.context);
        if (classSymbol.type != symtab.objectType && baseGenerator.isPure(classSymbol)) {
            return PureTypeCompiler.translateNewRecord(this, origin, newClass, context);
        }
        if (context.statementWriter() == null) {
            reporter.reportError(expr, "impureConstructorInPureContext");
            return JVerifyUtils.getReferenceHole(origin);
        } else {
            var rhs = generator.translateNewClassToAssignmentRhs(newClass, origin, context);
            return placeRhsIntoTemporaryAssignmentAndReturnResult(newClass.type, rhs, context);
        }
    }

    public AssignmentRhs translateNewClassToAssignmentRhs(JCTree.JCNewClass newClass, IOrigin origin, ExpressionContext context) {
        Symtab symtab = Symtab.instance(baseGenerator.context);
        if (newClass.type instanceof com.sun.tools.javac.code.Type.ArrayType) {
            throw new RuntimeException("not supported. should have already been lowered");
        }
        Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) TreeInfo.symbol(newClass.clazz);
        if (classSymbol.type != symtab.objectType && baseGenerator.isPure(classSymbol)) {
            var datatypeValue = PureTypeCompiler.translateNewRecord(this, origin, newClass, context);
            return new ExprRhs(origin, null, datatypeValue);
        }
        NameSegment classBaseType = getNewClassType(newClass);

        String ctorNameStr = baseGenerator.nameCompiler.getCompiledName(newClass.constructor, origin);
        Name ctorName = new Name(origin, ctorNameStr);
        var ty = new UserDefinedType(origin, new ExprDotName(origin, classBaseType, ctorName, null));

        var argBindings = ExpressionCompiler.createBindings(newClass.getArguments().stream().map(a -> toExpr(a, context)));
        return new AllocateClass(origin, null, ty, argBindings);
    }

    private NameSegment getNewClassType(JCTree.JCNewClass newClass) {
        var baseType = (UserDefinedType) baseGenerator.translateType(newClass.type, reporter.toOrigin(newClass));
        var baseNameSegment = (NameSegment)baseType.getNamePath();
        var baseName = baseNameSegment.getName();
        return new NameSegment(baseNameSegment.getOrigin(), baseGenerator.nameCompiler.CLASS_PREFIX + baseName,
                baseNameSegment.getOptTypeArguments());
    }

    Expression placeRhsIntoTemporaryAssignmentAndReturnResult(com.sun.tools.javac.code.Type type, AssignmentRhs rhs, ExpressionContext context) {
        var origin = rhs.getOrigin();
        Type translatedType = generator.translateType(type, origin, null);
        LocalVariable localVariable = new LocalVariable(origin, "impure" + NameCompiler.sep + context.getVariableSuffix(),
                translatedType, false);
        List<Expression> lhss = List.of(new IdentifierExpr(localVariable.getOrigin(), localVariable.getName()));
        List<AssignmentRhs> rhss = List.of(rhs);
        VarDeclStmt varDeclStmt = new VarDeclStmt(origin, null, List.of(localVariable),
                new AssignStatement(origin, null, lhss, rhss, false));

        context.statementWriter().accept(varDeclStmt);
        return new NameSegment(origin, localVariable.getName(), null);
    }

    private ExpressionWithFlows translateInstanceOf(JCTree.JCInstanceOf instanceOf, IOrigin origin, ExpressionContext context) {
        context = context.forbidImpure();
        var expression = toExpr(instanceOf.getExpression(), context);
        var jcType = baseGenerator.translateType(instanceOf.getType());
        TypeTestExpr result = new TypeTestExpr(origin, expression, jcType);
        List<FlowCast> flows = List.of();
        if (instanceOf.getPattern() != null) {
            if (instanceOf.getPattern() instanceof JCTree.JCBindingPattern bindingPattern) {
                Type translatedType = baseGenerator.translateType(instanceOf.getType());
                String compiledName = baseGenerator.nameCompiler.getCompiledName(bindingPattern.var.sym, bindingPattern.var);
                flows = List.of(new FlowCast(compiledName, new ConversionExpr(origin, expression, translatedType, ""), translatedType));
            } else {
                reporter.reportError(instanceOf, "notSupported", "Record patterns");
            }
        }
        return new ExpressionWithFlows(result, flows);
    }

    private Expression translateCast(JCTree.JCTypeCast cast, IOrigin origin, ExpressionContext context) {
        context = context.forbidImpure();
        var castExpr = toExpr(cast.getExpression(), context);
        var targetType = cast.type;
        var sourceType = cast.getExpression().type;

        if (targetType.getTag() == TypeTag.DOUBLE && isIntegralType(sourceType)) {
            var realCast = new ConversionExpr(origin, castExpr, new RealType(origin), "");
            return new ApplySuffix(origin, fp64Method(origin, "FromReal"), null,
                    new ActualBindings(List.of(new ActualBinding(null, realCast, false))), null);
        }
        
        if (isIntegralType(targetType) && sourceType.getTag() == TypeTag.DOUBLE) {
            var intResult = new ApplySuffix(origin, fp64Method(origin, "ToInt"), null,
                    new ActualBindings(List.of(new ActualBinding(null, castExpr, false))), null);
            var type = baseGenerator.translateType(cast);
            return type.equals(new IntType(origin)) ? intResult : new ConversionExpr(origin, intResult, type, "");
        }

        return new ConversionExpr(origin, castExpr, baseGenerator.translateType(cast), "");
    }

    private Expression translateArrayAccess(JCTree.JCArrayAccess arrayAccess, IOrigin origin) {
        throw new RuntimeException("not supported. should have already been lowered");
    }

    private ITEExpr translateConditional(JCTree.JCConditional conditional, IOrigin origin, ExpressionContext context) {
        context = context.forbidImpure();
        var conditionWithFlows = toExprWithFlows(conditional.getCondition(), context);

        Expression thenWithoutFlow = toExpr(conditional.getTrueExpression(), context);
        var thenBranch = applyFlowCastsToExpr(thenWithoutFlow, conditionWithFlows.flows());
        var elseBranch = toExpr(conditional.getFalseExpression(), context);
        return new ITEExpr(origin, false, conditionWithFlows.expression(), thenBranch, elseBranch);
    }

    public Expression applyFlowCastsToExpr(Expression expression, List<FlowCast> flowCasts) {
        var result = expression;
        for(var flowCast : flowCasts) {
            String name = flowCast.name();
            var origin = flowCast.expression().getOrigin();
            var returnVar = new BoundVar(expression.getOrigin(), new Name(origin, name), flowCast.type(), false);
            var lhs = new CasePattern<>(expression.getOrigin(), name, returnVar, null);
            result = new LetExpr(origin, List.of(lhs), List.of(flowCast.expression()), result, true, null);
        }
        return result;
    }

    private Expression translateUnary(JCTree.JCExpression expr, JCTree.JCUnary unary, IOrigin origin, ExpressionContext context) {
        context = context.forbidImpure();
        var innerExpr = toExpr(unary.getExpression(), context);
        JCTree.Tag tag = unary.getTag();
        switch (tag) {
            case JCTree.Tag.POSTINC, POSTDEC, JCTree.Tag.PREINC, JCTree.Tag.PREDEC -> {
                if (unary.type.getTag() == TypeTag.FLOAT) {
                    reporter.reportError(unary, "notSupported", "operator " + unary.getOperator());
                    return JVerifyUtils.getHole(origin);
                }
                if (context.statementWriter() == null) {
                    reporter.reportError(expr, "mutatingExpression", unary.getOperator().name.toString());
                    return JVerifyUtils.getHole(origin);
                }

                List<Expression> lhss = List.of(innerExpr);

                var opCode = (tag == JCTree.Tag.POSTINC || tag == JCTree.Tag.PREINC)
                        ? BinaryExprOpcode.Add : BinaryExprOpcode.Sub;
                var incrementValue = (unary.type.getTag() == TypeTag.DOUBLE) 
                        ? translateFp64Literal(origin, 1.0)
                        : new LiteralExpr(origin, 1);
                var incremented = new BinaryExpr(origin, opCode, innerExpr, incrementValue);
                List<AssignmentRhs> rhss = List.of(new ExprRhs(origin, null, incremented));
                context.statementWriter().accept(new AssignStatement(origin, null, lhss, rhss, false));
                return innerExpr;
            }
            case JCTree.Tag.NOT -> {
                return new UnaryOpExpr(origin, innerExpr, UnaryOpExprOpcode.Not);
            }
            case JCTree.Tag.NEG -> {
                return new NegationExpression(origin, innerExpr);
            }
            case JCTree.Tag.POS -> {
                return innerExpr;
            }
            default -> {
                reporter.reportError(unary, "notSupported", "operator " + unary.getOperator());
                return JVerifyUtils.getHole(origin);
            }
        }
    }

    private ExpressionWithFlows translateBinary(JCTree.JCBinary binary, ExpressionContext context) {
        context = context.forbidImpure();
        var leftWithFlows = toExprWithFlows(binary.getLeftOperand(), context.withExpectedType(binary.getLeftOperand().type));
        var rightWithFlows = toExprWithFlows(binary.getRightOperand(), context.withExpectedType(binary.getLeftOperand().type));
        Expression right;

        Symbol.OperatorSymbol operator = binary.getOperator();
        if (operator.name.contentEquals("&&")) {
            right = applyFlowCastsToExpr(rightWithFlows.expression(), leftWithFlows.flows());
        } else {
            right = rightWithFlows.expression();
        }
        var combined = new ArrayList<FlowCast>();
        combined.addAll(leftWithFlows.flows());
        combined.addAll(rightWithFlows.flows());
        return new ExpressionWithFlows(translateBinary(
                binary, binary.getLeftOperand().type, binary.getRightOperand().type,
                operator, leftWithFlows.expression(), right), combined);
    }

    private BiFunction<JCTree.JCIdent, IOrigin, Expression> handleIdentifier;

    public <T> T withOverrideTranslateIdentifier(Supplier<T> supplier,
                                                 BiFunction<JCTree.JCIdent, IOrigin, Expression> override)
    {
        var previous = handleIdentifier;
        handleIdentifier = override;
        var result = supplier.get();
        handleIdentifier = previous;
        return result;
    }

    public Expression translateIdentifier(JCTree.JCIdent identifier, IOrigin origin) {
        if (handleIdentifier == null) {
            return translateIdentifierNoOverride(identifier, origin);
        } else {
            return handleIdentifier.apply(identifier, origin);
        }
    }

    public Expression translateIdentifierNoOverride(JCTree.JCIdent identifier, IOrigin origin) {
        var identName = baseGenerator.nameCompiler.getCompiledName(identifier.sym, identifier);
        if (identName.contentEquals("this")) {
            return new ThisExpr(origin);
        }
        return new NameSegment(origin, identName, null);
    }

    public Expression translateLiteral(JCTree.JCLiteral literal, IOrigin origin) {
        if (literal.typetag == TypeTag.BOOLEAN) {
            return new LiteralExpr(reporter.toOrigin(literal), literal.getValue());
        }
        if (literal.typetag == TypeTag.CHAR) {
            var intValue = Integer.valueOf((char) literal.getValue());
            return new LiteralExpr(reporter.toOrigin(literal), intValue);
        }
        if (literal.typetag == TypeTag.DOUBLE) {
            return translateFp64Literal(reporter.toOrigin(literal), ((Number) literal.getValue()).doubleValue());
        }
        if (literal.typetag == TypeTag.FLOAT) {
            reporter.reportError(literal, "floatNotSupported");
            return JVerifyUtils.getHole(origin);
        }
        if (literal.getKind().equals(Tree.Kind.STRING_LITERAL)) {
            return translateStringLiteral(reporter.toOrigin(literal), literal);
        }
        return new LiteralExpr(origin, literal.getValue());
    }

    private NameSegment translateTypeApplication(JCTree.JCTypeApply typeApply, IOrigin origin, ExpressionContext context) {
        var type = toExpr((JCTree.JCExpression)typeApply.getType(), context);
        if (type instanceof NameSegment nameSegment) {
            List<Type> arguments;
            if (typeApply.getTypeArguments().isEmpty()) {
                // Occurs when the type arguments were inferred
                arguments = typeApply.type.getTypeArguments().stream().map(t ->
                        generator.translateType(t, origin, null)).toList();
            } else {
                arguments = typeApply.getTypeArguments().stream().map(baseGenerator::translateType).toList();
            }
            return new NameSegment(origin, nameSegment.getName(), arguments);
        }
        throw new RuntimeException("All Dafny type references are NameSegments, since we do not use Dafny modules");
    }

    private Expression translateFieldAccess(JCTree.JCFieldAccess fieldAccess, IOrigin origin, ExpressionContext context) {
        context = context.forbidImpure();
        if (fieldAccess.sym instanceof Symbol.ClassSymbol classSymbol) {
            // Ignore package qualification
            return new NameSegment(origin, baseGenerator.nameCompiler.getCompiledName(classSymbol, origin), List.of());
        }

        if (fieldAccess.sym instanceof Symbol.VarSymbol varSymbol &&
            varSymbol.owner instanceof Symbol.ClassSymbol ownerClass &&
            (ownerClass.fullname.contentEquals(Double.class.getName()) ||
             ownerClass.fullname.contentEquals(Double.class.getName() + "?static"))) {
            var fieldNameStr = fieldAccess.name.toString();
            return switch (fieldNameStr) {
                case "NaN" -> fp64Constant(origin, "NaN");
                case "POSITIVE_INFINITY" -> fp64Constant(origin, "PositiveInfinity");
                case "NEGATIVE_INFINITY" -> fp64Constant(origin, "NegativeInfinity");
                case "MAX_VALUE" -> fp64Constant(origin, "MaxValue");
                case "MIN_VALUE" -> fp64Constant(origin, "MinSubnormal");
                case "MIN_NORMAL" -> fp64Constant(origin, "MinNormal");
                default -> {
                    reporter.reportError(fieldAccess, "notSupported", "Double field " + fieldNameStr);
                    yield JVerifyUtils.getHole(origin);
                }
            };
        }

        com.sun.tools.javac.code.Type nonNullFieldContainerType = fieldAccess.sym.owner.type;
        var selectedExpr = toExpr(fieldAccess.selected, context.withExpectedType(nonNullFieldContainerType));
        // TODO does this work if the selected expression isn't trivially of array type?
        if (fieldAccess.selected.type instanceof ArrayType && fieldAccess.name.contentEquals("length")) {
            ExprDotName callee = new ExprDotName(origin, selectedExpr, reporter.getName(fieldAccess, "length"), null);
            return createCall(origin, callee, Stream.of(), context);
        }

        var fieldName = baseGenerator.nameCompiler.getCompiledName(fieldAccess.sym, fieldAccess);
        if (JVerifyUtils.isEnum(fieldAccess.selected)) {
            return new ApplySuffix(origin, new NameSegment(origin, fieldName, null),
                    null, new ActualBindings(List.of()), null);
        } else {
            boolean methodContainerTypeIsParameter = fieldAccess.selected.type instanceof com.sun.tools.javac.code.Type.TypeVar;
            if (methodContainerTypeIsParameter) {
                var classType = fieldAccess.sym.enclClass().type;
                // Dafny needs an explicit cast otherwise it won't find the members from the type parameter bounds
                selectedExpr = new ConversionExpr(origin, selectedExpr, baseGenerator.translateType(classType, origin), "");
            }
            return new ExprDotName(origin, selectedExpr, reporter.getName(fieldAccess, fieldName), null);
        }
    }

    public Expression translateMethodInvocation(JCTree.JCMethodInvocation invocation, IOrigin origin, ExpressionContext context) {
        var methodSymbol = (Symbol.MethodSymbol)TreeInfo.symbol(invocation.getMethodSelect());

        if (!((invocation.getMethodSelect() instanceof JCTree.JCFieldAccess fieldAccess) ||
             (invocation.getMethodSelect() instanceof JCTree.JCIdent))) {
            reporter.reportError(invocation, "notSupported", "call via method reference");
        }

        if (methodSymbol.owner instanceof Symbol.ClassSymbol ownerClass
                && ownerClass.isRecord()
                && invocation.getArguments().isEmpty()
        ) {
            // retrieving record component
            var component = ownerClass.getRecordComponents().stream()
                    .filter(comp -> comp.name.equals(methodSymbol.name))
                    .findAny();
            if (component.isPresent()) {
                final Expression receiver;
                var fieldNameStr = nameCompiler.getCompiledName(component.get(), origin);
                if (invocation.getMethodSelect() instanceof JCTree.JCFieldAccess fieldAccess) {
                    receiver = toExpr(fieldAccess.selected, context.withExpectedType(null));
                } else if (invocation.getMethodSelect() instanceof JCTree.JCIdent ident) {
                    return new NameSegment(origin, fieldNameStr, null);
                } else {
                    receiver = JVerifyUtils.getHole(origin);
                }
                var fieldName = reporter.getName(invocation.getMethodSelect(), fieldNameStr);
                return new ExprDotName(origin, receiver, fieldName, null);
            }
        }

        if (methodSymbol.owner instanceof Symbol.ClassSymbol ownerClass
                && ownerClass.fullname.contentEquals(Math.class.getName())) {
            var methodName = methodSymbol.name.toString();
            var argBindings = invocation.getArguments().stream()
                    .map(arg -> new ActualBinding(null, toExpr(arg, context.forbidImpure()), false))
                    .toList();

            return switch (methodName) {
                case "abs" -> {
                    if (!argBindings.isEmpty() && invocation.getArguments().get(0).type.getTag() == TypeTag.DOUBLE) {
                        var absMethod = fp64Method(origin, "Abs");
                        yield new ApplySuffix(origin, absMethod, null, new ActualBindings(argBindings), null);
                    }
                    reporter.reportError(origin, "notSupported", "Math.abs for integers");
                    yield JVerifyUtils.getHole(origin);
                }
                case "sqrt" -> {
                    if (!argBindings.isEmpty()) {
                        var sqrtMethod = fp64Method(origin, "Sqrt");
                        yield new ApplySuffix(origin, sqrtMethod, null, new ActualBindings(argBindings), null);
                    }
                    reporter.reportError(invocation, "notSupported", "Math." + methodName);
                    yield JVerifyUtils.getHole(origin);
                }
                case "min", "max" -> {
                    if (argBindings.size() == 2) {
                        var arg1 = argBindings.get(0).getActual();
                        var arg2 = argBindings.get(1).getActual();
                        if (invocation.getArguments().get(0).type.getTag() == TypeTag.DOUBLE) {
                            var method = fp64Method(origin, methodName.equals("min") ? "Min" : "Max");
                            yield new ApplySuffix(origin, method, null, new ActualBindings(argBindings), null);
                        }
                        var comparison = new BinaryExpr(origin,
                                methodName.equals("min") ? BinaryExprOpcode.Lt : BinaryExprOpcode.Gt,
                                arg1, arg2);
                        yield new ITEExpr(origin, false, comparison, arg1, arg2);
                    }
                    reporter.reportError(invocation, "notSupported", "Math." + methodName);
                    yield JVerifyUtils.getHole(origin);
                }
                default -> {
                    reporter.reportError(invocation, "notSupported", "Math method " + methodName);
                    yield JVerifyUtils.getHole(origin);
                }
            };
        }

        if (methodSymbol.owner instanceof Symbol.ClassSymbol ownerClass
                && ownerClass.fullname.contentEquals(Double.class.getName())) {
            var methodName = methodSymbol.name.toString();

            if (methodSymbol.isStatic()) {
                return switch (methodName) {
                    case "isNaN", "isInfinite", "isFinite" -> {
                        if (!invocation.getArguments().isEmpty()) {
                            var arg = toExpr(invocation.getArguments().get(0), context.forbidImpure());
                            if (arg instanceof LiteralExpr) {
                                arg = new ParensExpression(origin, arg);
                            }
                            var propertyName = switch (methodName) {
                                case "isNaN" -> "IsNaN";
                                case "isInfinite" -> "IsInfinite";
                                case "isFinite" -> "IsFinite";
                                default -> throw new IllegalStateException();
                            };
                            yield new ExprDotName(origin, arg, new Name(origin, propertyName), null);
                        }
                        reporter.reportError(invocation, "notSupported", "Double." + methodName);
                        yield JVerifyUtils.getHole(origin);
                    }
                    case "valueOf" -> {
                        if (!invocation.getArguments().isEmpty()) {
                            yield toExpr(invocation.getArguments().get(0), context.forbidImpure());
                        }
                        reporter.reportError(invocation, "notSupported", "Double.valueOf");
                        yield JVerifyUtils.getHole(origin);
                    }
                    default -> {
                        reporter.reportError(invocation, "notSupported", "Double method " + methodName);
                        yield JVerifyUtils.getHole(origin);
                    }
                };
            } else {
                return switch (methodName) {
                    case "doubleValue", "floatValue" -> {
                        if (invocation.getMethodSelect() instanceof JCTree.JCFieldAccess fieldAccess) {
                            yield toExpr(fieldAccess.selected, context);
                        }
                        reporter.reportError(invocation, "notSupported", "Double." + methodName);
                        yield JVerifyUtils.getHole(origin);
                    }
                    case "isNaN", "isInfinite" -> {
                        if (invocation.getMethodSelect() instanceof JCTree.JCFieldAccess fieldAccess) {
                            var receiver = toExpr(fieldAccess.selected, context);
                            var propertyName = methodName.equals("isNaN") ? "IsNaN" : "IsInfinite";
                            yield new ExprDotName(origin, receiver, new Name(origin, propertyName), null);
                        }
                        reporter.reportError(invocation, "notSupported", "Double." + methodName);
                        yield JVerifyUtils.getHole(origin);
                    }
                    default -> {
                        reporter.reportError(invocation, "notSupported", "Double method " + methodName);
                        yield JVerifyUtils.getHole(origin);
                    }
                };
            }
        }

        var target = toExpr(invocation.getMethodSelect(), context.withExpectedType(null));
        var isPure = utils.isPure(methodSymbol);
        var call = createCall(origin, target, invocation.getArguments().stream(), context);
        if (context.statementWriter() != null && !isPure && !context.allowImpure()) {
            return placeRhsIntoTemporaryAssignmentAndReturnResult(invocation.type, new ExprRhs(origin, null, call), context);
        }
        return call;
    }

    /**
     * Translates a binary operator expression to Dafny.
     *
     * @param rightType must be non-null if the operator is a comparison
     */
    public Expression translateBinary(
            JCTree node,
            com.sun.tools.javac.code.Type leftType,
            com.sun.tools.javac.code.@Nullable Type rightType,
            Symbol.OperatorSymbol operator,
            Expression left,
            Expression right) {
        var origin = reporter.toOrigin(node);
        var opName = operator.name.toString();

        if (opName.equals("+") && leftType.toString().contentEquals(String.class.getName())) {
            var callee = new ExprDotName(origin, left, new Name(origin, "concat"), null);
            return createCall2(origin, callee, Stream.of(right));
        }

        if (leftType.getTag() == TypeTag.DOUBLE && rightType != null && rightType.getTag() != TypeTag.DOUBLE) {
            if (rightType.isPrimitive() && rightType.getTag() != TypeTag.BOOLEAN) {
                JCTree.JCExpression rightOperand = (node instanceof JCTree.JCBinary binary)
                    ? binary.getRightOperand() : null;
                right = promoteToFp64(rightOperand, right, origin);
            }
        } else if (rightType != null && rightType.getTag() == TypeTag.DOUBLE && leftType.getTag() != TypeTag.DOUBLE) {
            if (leftType.isPrimitive() && leftType.getTag() != TypeTag.BOOLEAN) {
                JCTree.JCExpression leftOperand = (node instanceof JCTree.JCBinary binary)
                    ? binary.getLeftOperand() : null;
                left = promoteToFp64(leftOperand, left, origin);
            }
        }

        if ((opName.equals("==") || opName.equals("!=")) && leftType.getTag() == TypeTag.DOUBLE) {
            var equalMethod = fp64Method(origin, "Equal");
            var args = List.of(new ActualBinding(null, left, false),
                              new ActualBinding(null, right, false));
            var equalCall = new ApplySuffix(origin, equalMethod, null,
                                           new ActualBindings(args), null);
            if (opName.equals("!=")) {
                return new UnaryOpExpr(origin, equalCall, UnaryOpExprOpcode.Not);
            }
            return equalCall;
        }

        if (opName.equals("%") && (leftType.getTag() == TypeTag.DOUBLE || leftType.getTag() == TypeTag.FLOAT)) {
            reporter.reportError(node, "notSupported", "modulo operator (%) with floating-point types");
            return JVerifyUtils.getHole(origin);
        }

        if (leftType.getTag() == TypeTag.FLOAT) {
            reporter.reportError(node, "notSupported", "operator " + operator);
            return JVerifyUtils.getHole(origin);
        }

        if (opName.equals("==") || opName.equals("!=")) {
            assert rightType != null;

            // Some Java reference types are translated to Dafny value types
            // (e.g. String translates to seq<char16>, record classes translate to datatypes);
            // let's call these "Java-Reference-as-Dafny-Value types", or "JRDV types" for short.
            // Distinct heap objects of JRDV type are certainly not equal according to Java's "==",
            // but their translated values could be (structurally) equal according to Dafny's "==",
            // and this difference in semantics could lead to unsoundness.
            var isSafe = false;

            // If either operand is definitely null,
            // then comparison to a value of JRDV type will be rejected during Dafny resolution,
            // because the translation of a JRDV type is a Dafny value type by definition.
            isSafe |= leftType.getKind() == TypeKind.NULL || rightType.getKind() == TypeKind.NULL;

            // If one operand is of primitive type,
            // then the other operand must either also be of primitive type,
            // or of a boxed type (in which case the operand undergoes unboxing promotion).
            // In both cases the Java semantics are preserved in translation.
            isSafe |= leftType.isPrimitive() || rightType.isPrimitive();

            // If one operand is definitely not of a JRDV type,
            // then since we already checked that it's not of primitive type,
            // we know that it's of reference type and will be translated to a Dafny reference type.
            // The type of the other operand doesn't matter:
            // if it's of JRDV type then the comparison will be rejected during Dafny resolution.
            isSafe |= !isPossiblyJrdvType(leftType) || !isPossiblyJrdvType(rightType);

            if (!isSafe) {
                reporter.reportError(node, "equalityOperatorRestricted", opName);
                return JVerifyUtils.getHole(origin);
            }
        }

        var isBitwise = switch (opName) {
            case "&", "|", "^", "<<", ">>", ">>>" -> true;
            default -> false;
        };

        if (isBitwise) {
            reporter.reportError(node, "notSupported", "operator " + operator);
            return JVerifyUtils.getHole(origin);
        }
        BinaryExprOpcode dafnyOperator = toDafny(operator);
        if (dafnyOperator == null) {
            reporter.reportError(node, "notSupported", "operator " + operator);
            return JVerifyUtils.getHole(origin);
        }
        return new BinaryExpr(origin, dafnyOperator, left, right);
    }

    /**
     * Returns whether a value of the given type is possibly a "Java-Reference-as-Dafny-Value" type ("JRDV type").
     */
    private boolean isPossiblyJrdvType(com.sun.tools.javac.code.Type type) {
        if (type.isPrimitive()) {
            return false;
        }

        var symtab = Symtab.instance(this.baseGenerator.context);
        if (type.baseType() == symtab.objectType) {
            return !JVerifyUtils.isAnnotated(type, Impure.class);
        }

        var types = Types.instance(this.baseGenerator.context);
        return Stream.concat(primitiveTypes().stream(), jrdvTypes().stream())
                .anyMatch(t -> types.isAssignable(type, t) || types.isAssignable(t, type));
    }

    private List<com.sun.tools.javac.code.Type> primitiveTypes() {
        var symtab = Symtab.instance(this.baseGenerator.context);
        return List.of(
                symtab.booleanType,
                symtab.byteType,
                symtab.shortType,
                symtab.charType,
                symtab.intType,
                symtab.longType,
                symtab.floatType,
                symtab.doubleType
        );
    }

    /**
     * Returns a list of the "Java-Reference-as-Dafny-Value" types ("JRDV types").
     */
    private List<com.sun.tools.javac.code.Type> jrdvTypes() {
        var symtab = Symtab.instance(this.baseGenerator.context);
        return List.of(symtab.stringType, symtab.recordType);
    }

    /**
     * Translates the given string literal to a Dafny expression (of type {@code jstring}).
     * For ease of debugging, the translation is the equivalent Dafny string literal
     * if all characters in the string are printable ASCII or have (non-Unicode) escape sequences.
     * Otherwise, the translation is a sequence display of the characters' numeric values.
     */
    private static Expression translateStringLiteral(IOrigin origin, JCTree.JCLiteral literal) {
        assert literal.getKind().equals(Tree.Kind.STRING_LITERAL);
        var stringValue = (String) literal.getValue();

        var translatedChars = new StringBuilder();
        for (var charValue : stringValue.toCharArray()) {
            if (ESCAPED_CHARS.containsKey(charValue)) {
                translatedChars.append(ESCAPED_CHARS.get(charValue));
            } else if (charValue >= 0x20 && charValue <= 0x7e) {
                translatedChars.append(charValue);
            } else {
                // Fallback to sequence of numeric values
                var charExprs = stringValue.chars().boxed()
                        .map(c -> (Expression) new LiteralExpr(origin, c)).toList();
                NameSegment callee = new NameSegment(origin, "String", null);
                SeqDisplayExpr actual = new SeqDisplayExpr(origin, charExprs);
                return createCall2(origin, callee, Stream.of(actual));
            }
        }

        var stringExpr = new StringLiteralExpr(origin, translatedChars.toString(), false);
        NameSegment callee = new NameSegment(origin, "JString", null);
        return createCall2(origin, callee, Stream.of(stringExpr));
    }

    private static final Map<Character, String> ESCAPED_CHARS = Map.of(
            '\'', "\\'",
            '\"', "\\\"",
            '\\', "\\\\",
            '\0', "\\0",
            '\n', "\\n",
            '\r', "\\r",
            '\t', "\\t"
    );

    public ApplySuffix createCall(IOrigin origin, Expression callee,
                                  Stream<JCTree.JCExpression> arguments,
                                  ExpressionContext context) {
        return createCall2(origin, callee, arguments.map(a -> toExpr(a, context.forbidImpure())));
    }

    public static ApplySuffix createCall2(IOrigin origin, Expression callee, Stream<Expression> bindingList) {
        return new ApplySuffix(origin, callee, null,
                createBindings(bindingList), null);
    }

    public Expression translateFp64Literal(IOrigin origin, double value) {
        if (Double.isNaN(value)) {
            return fp64Constant(origin, "NaN");
        }
        if (value == Double.POSITIVE_INFINITY) {
            return fp64Constant(origin, "PositiveInfinity");
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return fp64Constant(origin, "NegativeInfinity");
        }
        if (value == Double.MAX_VALUE) {
            return fp64Constant(origin, "MaxValue");
        }
        if (value == Double.MIN_VALUE) {
            return fp64Constant(origin, "MinSubnormal");
        }
        if (value == Double.MIN_NORMAL) {
            return fp64Constant(origin, "MinNormal");
        }

        var literal = new DecimalLiteralExpr(origin, value);

        // Use ~ prefix if the decimal representation is not exactly representable in fp64.
        // A decimal is exact if its denominator (in lowest terms) is a power of 2.
        if (isExactlyRepresentableInDecimal(value)) {
            return literal;
        }

        return new ApproximateExpr(origin, literal);
    }

    private boolean isExactlyRepresentableInDecimal(double value) {
        if (value == 0.0) return true;

        String str = Double.toString(Math.abs(value));
        BigInteger numerator;
        BigInteger denominator = BigInteger.ONE;

        int eIndex = str.indexOf('E');
        String mantissaStr = (eIndex >= 0) ? str.substring(0, eIndex) : str;
        int exponent = (eIndex >= 0) ? Integer.parseInt(str.substring(eIndex + 1)) : 0;

        int dotIndex = mantissaStr.indexOf('.');
        if (dotIndex >= 0) {
            int decimalPlaces = mantissaStr.length() - dotIndex - 1;
            numerator = new BigInteger(mantissaStr.replace(".", ""));
            denominator = BigInteger.TEN.pow(decimalPlaces);
        } else {
            numerator = new BigInteger(mantissaStr);
        }

        if (exponent > 0) {
            numerator = numerator.multiply(BigInteger.TEN.pow(exponent));
        } else if (exponent < 0) {
            denominator = denominator.multiply(BigInteger.TEN.pow(-exponent));
        }

        BigInteger gcd = numerator.gcd(denominator);
        denominator = denominator.divide(gcd);

        return denominator.and(denominator.subtract(BigInteger.ONE)).equals(BigInteger.ZERO);
    }

    public Expression promoteToFp64(JCTree.@Nullable JCExpression javaExpr, Expression dafnyExpr, IOrigin origin) {
        if (javaExpr instanceof JCTree.JCLiteral literal &&
            (literal.typetag == TypeTag.INT || literal.typetag == TypeTag.LONG)) {
            double doubleValue = ((Number) literal.getValue()).doubleValue();
            return translateFp64Literal(origin, doubleValue);
        }
        var toReal = new ConversionExpr(origin, dafnyExpr, new RealType(origin), "");
        var fromRealCall = fp64Method(origin, "FromReal");
        var args = List.of(new ActualBinding(null, toReal, false));
        return new ApplySuffix(origin, fromRealCall, null, new ActualBindings(args), null);
    }

    public static boolean isIntegralType(com.sun.tools.javac.code.Type type) {
        return type != null && type.isPrimitive() &&
            (type.getTag() == TypeTag.INT || type.getTag() == TypeTag.LONG ||
             type.getTag() == TypeTag.SHORT || type.getTag() == TypeTag.BYTE);
    }

    private static NameSegment fp64Segment(IOrigin origin) {
        return new NameSegment(origin, "fp64", null);
    }

    private static Expression fp64Constant(IOrigin origin, String constantName) {
        return new ExprDotName(origin, fp64Segment(origin), new Name(origin, constantName), null);
    }

    private static Expression fp64Method(IOrigin origin, String methodName) {
        return new ExprDotName(origin, fp64Segment(origin), new Name(origin, methodName), null);
    }


    public static ActualBindings createBindings(Stream<Expression> bindingList) {
        return new ActualBindings(bindingList.map(expression ->
                new ActualBinding(null, expression, false)).toList());
    }
}
