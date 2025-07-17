package com.aws.jverify.verifier.compiler;

import com.aws.jverify.common.Common;
import com.aws.jverify.generated.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;

import java.util.ArrayList;
import java.util.List;

public class ContractCompiler {
    public final JavaToDafnyCompiler compiler;

    public ContractCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
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
}
