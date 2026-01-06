package com.aws.jverify.verifier.compiler.generator.dafny;

import com.aws.jverify.JVerify;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.aws.jverify.verifier.compiler.simplifications.JVerifyUtils;
import com.aws.jverify.verifier.compiler.simplifications.MethodOrLoopContractCompiler;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class JVerifyGhostExpressionCompiler extends WrappingDafnyGenerator {
    final ExpressionCompiler expressionCompiler;
    private final Reporter reporter;
    final BaseDafnyGenerator baseGenerator;
    private final DafnyGenerator generator;
    private final JVerifyUtils utils;
    private final TreeMaker treeMaker;
    private final Names names;
    MethodOrLoopContractCompiler contractCompiler;
    JVerifyIndex index;

    public JVerifyGhostExpressionCompiler(Context context, 
                                          DafnyGenerator next) {
        super(next);
        this.baseGenerator = context.get(BaseDafnyGenerator.class);
        contractCompiler = MethodOrLoopContractCompiler.instance(context);
        utils = JVerifyUtils.instance(context);
        index = JVerifyIndex.instance(context);
        treeMaker = TreeMaker.instance(context);
        names = Names.instance(context);
        generator = context.get(DafnyGenerator.class);
        this.expressionCompiler = baseGenerator.expressionCompiler;
        reporter = baseGenerator.reporter;
    }

    /**
     * Handles Java types from the JVerify library that are stand-ins
     * for Dafny types defined in the JVerify prelude or built-in to Dafny.
     */
    @Override
    public Type translateClassType(IOrigin origin, JCTree.JCModifiers additionalModifiers, com.sun.tools.javac.code.Type.ClassType classType) {
        var className = classType.asElement().flatName();
        if (className.toString().equals(JVerify.Sequence.class.getName())) {
            var typeArguments = classType.getTypeArguments().stream().map(a -> baseGenerator.translateType(a, origin)).toList();
            if (typeArguments.stream().anyMatch(Objects::isNull)) {
                return next.translateClassType(origin, additionalModifiers, classType);
            }
            return new SeqType(origin, typeArguments);
        }
        if (className.toString().equals(JVerify.IntSequence.class.getName())) {
            return new SeqType(origin, List.of(new UserDefinedType(origin, new NameSegment(origin, "int32", null))));
        }
        if (className.toString().equals(JVerify.Set.class.getName())) {
            var arguments = classType.getTypeArguments().stream().map(a -> baseGenerator.translateType(a, origin)).toList();
            return new SetType(origin, arguments, true);
        }
        if (className.toString().equals(JVerify.Map.class.getName())) {
            var arguments = classType.getTypeArguments().stream().map(a -> baseGenerator.translateType(a, origin)).toList();
            return new MapType(origin, arguments, true);
        }
        if (className.toString().equals(JVerify.CharJSequence.class.getName())) {
            return new SeqType(origin, List.of(BaseDafnyGenerator.getChar16Type(origin)));
        }
        if (className.toString().equals(JVerify.IntSequence.class.getName())) {
            return new SeqType(origin, List.of(new IntType(origin)));
        }
        return next.translateClassType(origin, additionalModifiers, classType);
    }

    @Override
    public ExpressionWithFlows toExprWithFlows(JCTree.JCExpression expr, IOrigin originOverride, ExpressionContext context) {
        if (expr instanceof JCTree.JCMethodInvocation invocation) {
            return translateMethodInvocation(invocation, originOverride, context);
        }
        return super.toExprWithFlows(expr, originOverride, context);
    }

    /**
     * Handles translating JVerify library methods
     *
     * <p>Note: header methodContracts like {@link JVerify#precondition(boolean)}
     * and {@link JVerify#postcondition(boolean)}
     * must be translated by {@link BlockCompiler#translateStatement(JCTree.JCStatement)},
     * not here.
     */
    public ExpressionWithFlows translateMethodInvocation(JCTree.JCMethodInvocation invocation, IOrigin originOverride, ExpressionContext context) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> reporter.toOrigin(invocation));
        var jverifyMethod = BaseDafnyGenerator.getJVerifyMethod(invocation);
        if (jverifyMethod == null) {
            return super.toExprWithFlows(invocation, origin, context);
        }

        return new ExpressionWithFlows(translateJVerifyMethodInvocation(invocation, jverifyMethod, origin));
    }

    private Expression translateJVerifyMethodInvocation(JCTree.JCMethodInvocation invocation, Symbol.MethodSymbol jverifyMethod, IOrigin origin) {
        var receiver = invocation.getMethodSelect() instanceof JCTree.JCFieldAccess fieldAccess
                ? fieldAccess.selected
                : null;
        var methodName = jverifyMethod.getQualifiedName().toString();
        var args = invocation.getArguments();
        switch (methodName) {
            case "forall", "exists" -> {
                if (args.size() != 1) {
                    throw new JavaViolationException("A %s call must have exactly one argument".formatted(methodName));
                }
                if (!(args.getFirst() instanceof JCTree.JCLambda lambda)) {
                    reporter.reportError(args.getFirst(), "argumentMustBeLambda", methodName);
                    return JVerifyUtils.getHole(origin);
                }
                var boundVars = lambda.params.stream().map(param -> {
                    var paramOrigin = reporter.toOrigin(lambda);
                    var paramName = new Name(paramOrigin, param.getName().toString());
                    var paramType = generator.translateType(param.getType().type, paramOrigin, param.getModifiers());
                    return new BoundVar(paramOrigin, paramName, paramType, false);
                }).toList();
                var body = expressionCompiler.toExpr(lambda.getBody(), ExpressionContext.Pure);
                if (body == null) {
                    return null;
                }
                if ("forall".equals(methodName)) {
                    return new ForallExpr(origin, boundVars, null, body, null);
                } else {
                    return new ExistsExpr(origin, boundVars, null, body, null);
                }
            }
            case "everything" -> {
                return new WildcardExpr(origin);
            }
            case "cast" -> {
                return generator.toExpr(args.getFirst(), null, ExpressionContext.Pure);
            }
            case "elements" -> {
                return new SeqDisplayExpr(origin, args.map(e ->
                        generator.toExpr(e, null, ExpressionContext.Pure)));
            }
            case "sequence" -> {
                // array conversion to sequence by appending "[..]", optionally with lo/hi
                assert args.size() == 1;
                var array = args.get(0);
                NameSegment callee = new NameSegment(origin, "toSequence", null);
                return expressionCompiler.createCall(origin, callee, Stream.of(array), ExpressionContext.Pure);
            }
            case "range" -> {
                NameSegment callee = new NameSegment(origin, "intSequenceRange", null);
                return expressionCompiler.createCall(origin, callee, Stream.of(args.getFirst(), args.get(1)), ExpressionContext.Pure);
            }
            case "get" -> {
                var seq = expressionCompiler.toExpr(receiver, ExpressionContext.Pure);
                var index = expressionCompiler.toExpr(args.getFirst(), ExpressionContext.Pure);
                return new SeqSelectExpr(origin, true, seq, index, null, null);
            }
            case "drop" -> {
                return toSubsequence(origin, receiver, args.getFirst(), null);
            }
            case "take" -> {
                return toSubsequence(origin, receiver, null, args.getFirst());
            }
            case "subsequence" -> {
                return toSubsequence(origin, receiver, args.get(0), args.get(1));
            }
            case "concat" -> {
                var left = expressionCompiler.toExpr(receiver, ExpressionContext.Pure);
                var right = expressionCompiler.toExpr(args.getFirst(), ExpressionContext.Pure);
                return new BinaryExpr(origin, BinaryExprOpcode.Add, left, right);
            }
            case "contains" -> {
                var element = expressionCompiler.toExpr(args.getFirst(), ExpressionContext.Pure);
                var collection = expressionCompiler.toExpr(receiver, ExpressionContext.Pure);
                return new BinaryExpr(origin, BinaryExprOpcode.In, element, collection);
            }
            case "size" -> {
                var collection = expressionCompiler.toExpr(receiver, ExpressionContext.Pure);
                return new UnaryOpExpr(origin, collection, UnaryOpExprOpcode.Cardinality);
            }
            case "entries" -> {
                var collection = expressionCompiler.toExpr(receiver, ExpressionContext.Pure);
                return new ExprDotName(origin, collection, new Name(origin, "Items"), null);
            }
            case "old" -> {
                var element = expressionCompiler.toExpr(args.getFirst(), ExpressionContext.Pure);
                return new OldExpr(origin, element, null);
            }
            case "fresh" -> {
                var element = expressionCompiler.toExpr(args.getFirst(), ExpressionContext.Pure);
                return new FreshExpr(origin, element, null);
            }
            case "implies" -> {
                var antecedent = expressionCompiler.toExpr(args.getFirst(), ExpressionContext.Pure);
                var consequent = expressionCompiler.toExpr(args.get(1), ExpressionContext.Pure);
                return new BinaryExpr(origin, BinaryExprOpcode.Imp, antecedent, consequent);
            }
            case "all", "map" -> {
                return toSetComprehension(origin, methodName, receiver, args);
            }
            case "jequals" -> {
                var left = expressionCompiler.toExpr(args.getFirst(), ExpressionContext.Pure);
                var right = expressionCompiler.toExpr(args.get(1), ExpressionContext.Pure);
                return new BinaryExpr(origin, BinaryExprOpcode.Eq, left, right);
            }
            case "isAbstract" -> {
                reporter.reportError(invocation.getMethodSelect(), "notSupported", "isAbstract() in clauses other than a precondition");
                return JVerifyUtils.getHole(origin);
            }
            case "isAbstractBoolean" -> {
                reporter.reportError(invocation.getMethodSelect(), "notSupported", "isAbstractBoolean() in clauses other than a precondition");
                return JVerifyUtils.getHole(origin);
            }
        }

        reporter.reportError(invocation.getMethodSelect(), "notSupported", "library method %s".formatted(jverifyMethod));
        return JVerifyUtils.getHole(origin);
    }

    private SeqSelectExpr toSubsequence(IOrigin origin, JCTree.JCExpression seqOrArray, JCTree.@Nullable JCExpression lo, JCTree.@Nullable JCExpression hi) {
        var seqOrArrayExpr = expressionCompiler.toExpr(seqOrArray, ExpressionContext.Pure);
        var loExpr = lo == null ? null : expressionCompiler.toExpr(lo, ExpressionContext.Pure);
        var hiExpr = hi == null ? null : expressionCompiler.toExpr(hi, ExpressionContext.Pure);
        return new SeqSelectExpr(origin, false, seqOrArrayExpr, loExpr, hiExpr, null);
    }

    private SetComprehension toSetComprehension(IOrigin origin, String methodName, JCTree.JCExpression receiver, List<JCTree.JCExpression> args) {
        if (args.size() != 1) {
            throw new JavaViolationException("A %s call must have exactly one argument".formatted(methodName));
        }
        if (!(args.getFirst() instanceof JCTree.JCLambda lambda)) {
            reporter.reportError(args.getFirst(), "argumentMustBeLambda", methodName);
            return null;
        }
        var parameter = lambda.params.getFirst();
        var paramName = parameter.getName().toString();
        var type = generator.translateType(parameter.type, reporter.toOrigin(parameter), null);
        var boundVar = new BoundVar(origin, new Name(origin, paramName), type, false);
        var body = baseGenerator.expressionCompiler.toExpr(lambda.getBody(), ExpressionContext.Pure);
        if ("all".equals(methodName)) {
            return new SetComprehension(origin, List.of(boundVar), body, new IdentifierExpr(origin, paramName), null, true);
        } else {
            var source = baseGenerator.expressionCompiler.toExpr(receiver, ExpressionContext.Pure);
            var range = new BinaryExpr(origin, BinaryExprOpcode.In, new IdentifierExpr(origin, paramName), source);
            return new SetComprehension(origin, List.of(boundVar), range, body, null, true);
        }
    }
}
