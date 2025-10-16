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
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
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
    private final JVerifyUtils utils;
    
    DafnyGenerator getGenerator() { return baseGenerator.getFinalGenerator();}

    public ExpressionCompiler(BaseDafnyGenerator baseGenerator) {
        this.baseGenerator = baseGenerator;
        reporter = Reporter.instance(baseGenerator.context);
        utils = JVerifyUtils.instance(baseGenerator.context);
    }

    public ExpressionWithFlows toExprWithFlows(JCTree tree, ExpressionContext context) {
        if (tree instanceof JCTree.JCExpression expression) {
            return toExprWithFlows(expression, context);
        }
        baseGenerator.reportError(tree, "notSupported", tree.getClass().getSimpleName() + " as an expression");
        return new ExpressionWithFlows(BaseDafnyGenerator.getHole(baseGenerator.toOrigin(tree)));
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
            return BaseDafnyGenerator.getHole(origin);
        }

        var translatedCases = patternBodies.stream().map(patternBody -> {
            var caseOrigin = baseGenerator.toOrigin(patternBody.cas());
            var body = patternBody.body();
            final Expression translatedBody;

            // A switch rule introduces either an expression, a block, or a throw statement.
            if (body == null) {
                // This only happens for statement labels, which would have already raised an error in translateSwitchLabels
                translatedBody = BaseDafnyGenerator.getHole(origin);
            } else if (body instanceof JCTree.JCExpression expr) {
                translatedBody = toExpr(expr, newContext.withExpectedType(switchExpr.type));
            } else {
                var bodyKind = body instanceof JCTree.JCBlock ? "block" : "throw statement";
                baseGenerator.reportError(body, "notSupported", "switch rule %s".formatted(bodyKind));
                translatedBody = BaseDafnyGenerator.getHole(caseOrigin);
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
            var origin = baseGenerator.toOrigin(statement);
            if (statement instanceof JCTree.JCVariableDecl variableDecl) {
                if (variableDecl.init == null) {
                    baseGenerator.reportError(statement, "pureAssignmentNeedsInitializer", variableDecl.name.toString());
                    return result;
                }
                var type = baseGenerator.translateType(variableDecl.type, baseGenerator.toOrigin(variableDecl));
                String name = baseGenerator.nameCompiler.getCompiledName(variableDecl.sym, variableDecl);
                var returnVar = new BoundVar(origin, new Name(origin, name), type, false);
                var lhs = new CasePattern<>(origin, name, returnVar, null);
                result = new LetExpr(origin, List.of(lhs), List.of(toExpr(variableDecl.init, context)), result, true, null); 
            } else if (statement instanceof JCTree.JCIf ifStatement && ifStatement.getElseStatement() == null) {
                ExpressionWithFlows conditionWithFlows = toExprWithFlows(ifStatement.getCondition(), context);
                var thenExpression = toExpr(ifStatement.getThenStatement(), context);
                thenExpression = applyFlowCastsToExpr(thenExpression, conditionWithFlows.flows());
                result = new ITEExpr(baseGenerator.toOrigin(ifStatement), false,
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
                baseGenerator.reportError(statement, "pureBlockNotLastMustBeVariableDeclaration");
                result = BaseDafnyGenerator.getHole(origin);
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
            return new AssertStmt(baseGenerator.toOrigin(invocation), null,
                    toExpr(invocation.args.getFirst(), expressionContext), null);
        } if (name.equals("assume")) {
            if (invocation.args.size() != 1) {
                throw new JavaViolationException("Check should have a single argument");
            }
            return new AssumeStmt(baseGenerator.toOrigin(invocation), null,
                    toExpr(invocation.args.getFirst(), expressionContext));
        }
        return null;
    }

    private Expression toExpr(JCTree.JCStatement statement, ExpressionContext context) {
        IOrigin origin = baseGenerator.toOrigin(statement);
        context = context.forbidImpure();
        return switch (statement) {
            case JCTree.JCBlock block -> {        
                if (block.getStatements().isEmpty()) {
                    baseGenerator.reportError(block, "pureMethodLastStatement");
                    yield BaseDafnyGenerator.getHole(origin);
                } else {
                    yield toExprWithFlows(block.getStatements(), context);
                }
            }
            case JCTree.JCIf ifStatement -> {
                if (ifStatement.getElseStatement() == null) {
                    baseGenerator.reportError(statement, "pureMethodEndingIfThen");
                    yield BaseDafnyGenerator.getHole(origin);
                }
                ExpressionWithFlows conditionWithFlows = toExprWithFlows(ifStatement.getCondition(), context);
                yield new ITEExpr(origin, false,
                        conditionWithFlows.expression(),
                    applyFlowCastsToExpr(toExpr(ifStatement.getThenStatement(), context), conditionWithFlows.flows()),
                    toExpr(ifStatement.getElseStatement(), context));
            }
            case JCTree.JCReturn returnStatement -> toExpr(returnStatement.expr, context.withExpectedType(returnStatement.type));
            default -> {
                baseGenerator.reportError(statement, "pureMethodLastStatement");
                yield BaseDafnyGenerator.getHole(origin);
            }
        };
    }

    public Expression toExpr(JCTree tree, ExpressionContext context) {
        if (tree instanceof JCTree.JCExpression expression) {
            return getGenerator().toExprWithFlows(expression, null, context).expression();
        }
        baseGenerator.reportError(tree, "notSupported", tree.getClass().getSimpleName() + " as an expression");
        return BaseDafnyGenerator.getHole(baseGenerator.toOrigin(tree));
    }
    
    public Expression toExpr(JCTree.JCExpression expr, ExpressionContext context) {
        return getGenerator().toExprWithFlows(expr, null, context).expression();
    }
    
    public ExpressionWithFlows toExprWithFlows(JCTree.JCExpression expr, ExpressionContext context) {
        return getGenerator().toExprWithFlows(expr, null, context);
    }

    public ExpressionWithFlows toExprBase(JCTree.JCExpression expr, IOrigin originOverride, ExpressionContext context) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> baseGenerator.toOrigin(expr));
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
                    baseGenerator.reportError(expr, "mutatingExpression", assignOp.getOperator().name.toString() + "=");
                    return new ExpressionWithFlows(BaseDafnyGenerator.getHole(origin));
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
        baseGenerator.reportError(expr, "notSupported", expr.getClass().getSimpleName() + " in an expression");
        return new ExpressionWithFlows(BaseDafnyGenerator.getHole(origin));
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
            baseGenerator.reportError(assign, "notSupported", assign.getClass().getSimpleName() + " in a pure expression");
            return BaseDafnyGenerator.getHole(origin);
        }
    }

    public AssignmentRhs toAssignmentRhs(JCTree.JCExpression expr, ExpressionContext expressionContext) {
        return toAssignmentRhs(expr, null, expressionContext);
    }

    public AssignmentRhs toAssignmentRhs(JCTree.JCExpression expr, IOrigin originOverride, ExpressionContext expressionContext) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> baseGenerator.toOrigin(expr));
        switch (expr) {
            case JCTree.JCNewClass newClass -> {
                return baseGenerator.getFinalGenerator().translateNewClassToAssignmentRhs(newClass, origin, expressionContext);
            }
            case JCTree.JCNewArray _ -> {
                throw new RuntimeException("not supported. should have already been lowered");
            }
            default -> {
            }
        }
        var dafnyExpr = getGenerator().toExpr(expr, origin, expressionContext);
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
            baseGenerator.reportError(expr, "notSupported",
                    "using 'new' in a pure expression to create an instance of an impure type");
            return BaseDafnyGenerator.getReferenceHole(origin);
        } else {
            var rhs = getGenerator().translateNewClassToAssignmentRhs(newClass, origin, context);
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
        var baseType = (UserDefinedType) baseGenerator.translateType(newClass.type, baseGenerator.toOrigin(newClass));
        var baseNameSegment = (NameSegment)baseType.getNamePath();
        var baseName = baseNameSegment.getName();
        return new NameSegment(baseNameSegment.getOrigin(), baseGenerator.nameCompiler.CLASS_PREFIX + baseName,
                baseNameSegment.getOptTypeArguments());
    }
    
    Expression placeRhsIntoTemporaryAssignmentAndReturnResult(com.sun.tools.javac.code.Type type, AssignmentRhs rhs, ExpressionContext context) {
        var origin = rhs.getOrigin();
        List<AssignmentRhs> rhss = List.of(rhs);
        
        Type translatedType = this.baseGenerator.getFinalGenerator().translateType(type, origin, null);
        LocalVariable localVariable = new LocalVariable(origin, "impure" + NameCompiler.sep + context.getVariableSuffix(),
                translatedType, false);
        List<Expression> lhss = List.of(new IdentifierExpr(localVariable.getOrigin(), localVariable.getName()));
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

    private ConversionExpr translateCast(JCTree.JCTypeCast cast, IOrigin origin, ExpressionContext context) {
        context = context.forbidImpure();
        var castExpr = toExpr(cast.getExpression(), context);

        var type = baseGenerator.translateType(cast);
        return new ConversionExpr(origin, castExpr, type, "");
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
                if (unary.type.getTag() == TypeTag.FLOAT || unary.type.getTag() == TypeTag.DOUBLE) {
                    baseGenerator.reportError(unary, "notSupported", "operator " + unary.getOperator());
                    return BaseDafnyGenerator.getHole(origin);
                }
                if (context.statementWriter() == null) {
                    baseGenerator.reportError(expr, "mutatingExpression", unary.getOperator().name.toString());
                    return BaseDafnyGenerator.getHole(origin);
                }

                List<Expression> lhss = List.of(innerExpr);

                var opCode = (tag == JCTree.Tag.POSTINC || tag == JCTree.Tag.PREINC)
                        ? BinaryExprOpcode.Add : BinaryExprOpcode.Sub;
                var incremented = new BinaryExpr(origin, opCode, innerExpr, new LiteralExpr(origin, 1));
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
                baseGenerator.reportError(unary, "notSupported", "operator " + unary.getOperator());
                return BaseDafnyGenerator.getHole(origin);
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
            return new LiteralExpr(baseGenerator.toOrigin(literal), literal.getValue());
        }
        if (literal.typetag == TypeTag.CHAR) {
            var intValue = Integer.valueOf((char) literal.getValue());
            return new LiteralExpr(baseGenerator.toOrigin(literal), intValue);
        }
        if (literal.getKind().equals(Tree.Kind.STRING_LITERAL)) {
            return translateStringLiteral(baseGenerator.toOrigin(literal), literal);
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
                        baseGenerator.getFinalGenerator().translateType(t, origin, null)).toList();
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
        com.sun.tools.javac.code.Type nonNullFieldContainerType = fieldAccess.sym.owner.type;
        var selectedExpr = toExpr(fieldAccess.selected, context.withExpectedType(nonNullFieldContainerType));
        // TODO does this work if the selected expression isn't trivially of array type?
        if (fieldAccess.selected.type instanceof ArrayType && fieldAccess.name.contentEquals("length")) {
            ExprDotName callee = new ExprDotName(origin, selectedExpr, reporter.getName(fieldAccess, "length"), null);
            return createCall(origin, callee, Stream.of(), context);
        }
        
        var fieldName = baseGenerator.nameCompiler.getCompiledName(fieldAccess.sym, fieldAccess);
        if (baseGenerator.isEnum(fieldAccess.selected)) {
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
            baseGenerator.reportError(invocation, "notSupported", "call via method reference");
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
                var fieldNameStr = baseGenerator.nameCompiler.getCompiledName(component.get(), origin);
                if (invocation.getMethodSelect() instanceof JCTree.JCFieldAccess fieldAccess) {
                    receiver = toExpr(fieldAccess.selected, context.withExpectedType(null));
                } else if (invocation.getMethodSelect() instanceof JCTree.JCIdent ident) {
                    return new NameSegment(origin, fieldNameStr, null);
                } else {
                    receiver = BaseDafnyGenerator.getHole(origin);
                }
                var fieldName = reporter.getName(invocation.getMethodSelect(), fieldNameStr);
                return new ExprDotName(origin, receiver, fieldName, null);
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
        var origin = baseGenerator.toOrigin(node);
        var opName = operator.name.toString();
        if (leftType.getTag() == TypeTag.FLOAT || leftType.getTag() == TypeTag.DOUBLE) {
            baseGenerator.reportError(node, "notSupported", "operator " + operator);
        }

        if (opName.equals("+") && leftType.toString().contentEquals(String.class.getName())) {
            var callee = new ExprDotName(origin, left, new Name(origin, "concat"), null);
            return createCall2(origin, callee, Stream.of(right));
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
                baseGenerator.reportError(node, "equalityOperatorRestricted", opName);
                return BaseDafnyGenerator.getHole(origin);
            }
        }

        var isBitwise = switch (opName) {
            case "&", "|", "^", "<<", ">>", ">>>" -> true;
            default -> false;
        };

        if (isBitwise) {
            baseGenerator.reportError(node, "notSupported", "operator " + operator);
            return BaseDafnyGenerator.getHole(origin);
        }
        BinaryExprOpcode dafnyOperator = toDafny(operator);
        if (dafnyOperator == null) {
            baseGenerator.reportError(node, "notSupported", "operator " + operator);
            return BaseDafnyGenerator.getHole(origin);
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
            return !baseGenerator.isAnnotated(type, Impure.class);
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

    public static ActualBindings createBindings(Stream<Expression> bindingList) {
        return new ActualBindings(bindingList.map(expression ->
                new ActualBinding(null, expression, false)).toList());
    }
}
