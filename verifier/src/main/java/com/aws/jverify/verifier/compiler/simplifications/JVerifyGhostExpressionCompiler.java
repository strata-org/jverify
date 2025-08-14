package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.JVerify;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.BlockCompiler;
import com.aws.jverify.verifier.compiler.ExpressionCompiler;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class JVerifyGhostExpressionCompiler {
    final ExpressionCompiler expressionCompiler;
    final JavaToDafnyCompiler compiler;

    public JVerifyGhostExpressionCompiler(ExpressionCompiler expressionCompiler) {
        this.expressionCompiler = expressionCompiler;
        this.compiler = expressionCompiler.compiler;
    }

    /**
     * Translates the specified library method invocation to a Dafny expression,
     * or returns {@code null} if the invocation is not a JVerify library method.
     *
     * <p>Note: header methodContracts like {@link JVerify#precondition(boolean)}
     * and {@link JVerify#postcondition(boolean)}
     * must be translated by {@link BlockCompiler#translateStatement(JCTree.JCStatement)},
     * not here.
     */
    @Nullable
    public Expression jverifyLibMethodToExpr(JCTree.JCMethodInvocation invocation) {
        var jverifyMethod = JavaToDafnyCompiler.getJVerifyMethod(invocation);
        if (jverifyMethod == null) {
            return null;
        }

        var origin = compiler.toOrigin(invocation);
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
                    compiler.reportError(args.getFirst(), "argumentMustBeLambda", methodName);
                    return null;
                }
                var boundVars = lambda.params.stream().map(param -> {
                    var paramOrigin = compiler.toOrigin(lambda);
                    var paramName = new Name(paramOrigin, param.getName().toString());
                    var paramType = compiler.translateType(param.getType().type, paramOrigin, param.getModifiers());
                    return new BoundVar(paramOrigin, paramName, paramType, false);
                }).toList();
                var body = expressionCompiler.toExpr(lambda.getBody());
                if (body == null) {
                    return null;
                }
                if ("forall".equals(methodName)) {
                    return new ForallExpr(origin, boundVars, null, body, null);
                } else {
                    return new ExistsExpr(origin, boundVars, null, body, null);
                }
            }
            case "sequence" -> {
                // array conversion to sequence by appending "[..]", optionally with lo/hi
                var array = args.get(0);
                var fromIndex = args.length() > 1 ? args.get(1) : null;
                var toIndex = args.length() > 2 ? args.get(2) : null;
                return toSubsequence(origin, array, fromIndex, toIndex);
            }
            case "get" -> {
                var seq = expressionCompiler.toExpr(receiver);
                var index = expressionCompiler.toExpr(args.getFirst());
                return new SeqSelectExpr(compiler.toOrigin(invocation), true, seq, index, null, null);
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
            case "contains" -> {
                var element = expressionCompiler.toExpr(args.getFirst());
                var collection = expressionCompiler.toExpr(receiver);
                return new BinaryExpr(compiler.toOrigin(invocation), BinaryExprOpcode.In, element, collection);
            }
            case "size" -> {
                var collection = expressionCompiler.toExpr(receiver);
                return new UnaryOpExpr(compiler.toOrigin(invocation), collection, UnaryOpExprOpcode.Cardinality);
            }
            case "entries" -> {
                var collection = expressionCompiler.toExpr(receiver);
                return new ExprDotName(compiler.toOrigin(invocation), collection, new Name(origin, "Items"), null);
            }
            case "old" -> {
                var element = expressionCompiler.toExpr(args.getFirst());
                return new OldExpr(compiler.toOrigin(invocation), element, null);
            }
            case "fresh" -> {
                var element = expressionCompiler.toExpr(args.getFirst());
                return new FreshExpr(compiler.toOrigin(invocation), element, null);
            }
            case "contractOf" -> {
                // This is a no-op for the translation to Dafny,
                // only provided so that the contracts resolve at the Java level.
                return expressionCompiler.toExpr(args.getFirst());
            }
            case "all", "map" -> {
                if (args.size() != 1) {
                    throw new JavaViolationException("A %s call must have exactly one argument".formatted(methodName));
                }
                if (!(args.getFirst() instanceof JCTree.JCLambda lambda)) {
                    compiler.reportError(args.getFirst(), "argumentMustBeLambda", methodName);
                    return null;
                }
                var parameter = lambda.params.getFirst();
                var paramName = parameter.getName().toString();
                var type = compiler.translateType(parameter.type, compiler.toOrigin(parameter), null);
                var boundVar = new BoundVar(origin, new Name(origin, paramName), type, false);
                var body = compiler.expressionCompiler.toExpr(lambda.getBody());
                if ("all".equals(methodName)) {
                    return new SetComprehension(origin, List.of(boundVar), body, new IdentifierExpr(origin, paramName), null, true);
                } else {
                    var source = compiler.expressionCompiler.toExpr(receiver);
                    var range = new BinaryExpr(origin, BinaryExprOpcode.In, new IdentifierExpr(origin, paramName), source);
                    return new SetComprehension(origin, List.of(boundVar), range, body, null, true);
                }
            }
        }

        compiler.reportError(invocation.getMethodSelect(), "notSupported", "library method %s".formatted(jverifyMethod));
        return null;
    }

    private SeqSelectExpr toSubsequence(IOrigin origin, JCTree.JCExpression seqOrArray, JCTree.@Nullable JCExpression lo, JCTree.@Nullable JCExpression hi) {
        var seqOrArrayExpr = expressionCompiler.toExpr(seqOrArray);
        var loExpr = lo == null ? null : expressionCompiler.toExpr(lo);
        var hiExpr = hi == null ? null : expressionCompiler.toExpr(hi);
        return new SeqSelectExpr(origin, false, seqOrArrayExpr, loExpr, hiExpr, null);
    }
}
