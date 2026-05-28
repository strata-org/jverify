package org.strata.jverify.contracts2jqwik;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;

/**
 * Translates contract clauses into the corresponding jqwik constructs.
 *
 * <p>Translation rules:</p>
 * <ul>
 *     <li>{@code precondition(P)} → {@code Assume.that(P);} where P is emitted
 *         verbatim (the original expression AST is reused).</li>
 *     <li>A single {@code postcondition((T r) -> Q)} →
 *         {@code var r = method(args); return Q;}.</li>
 *     <li>Multiple postconditions are conjoined with {@code &&} in source order:
 *         {@code [(T r) -> Q1, (T r) -> Q2, P3]} →
 *         {@code var r = method(args); return Q1 && Q2 && P3;}.
 *         All lambda postconditions in a method must use the same parameter
 *         name (since they all bind the same return value).</li>
 *     <li>{@code postcondition(boolExpr)} (no return binding) → translated as
 *         a conjunct that doesn't reference the result.</li>
 *     <li>Quantifiers and {@code old(...)} cause the clause to be skipped with
 *         a compiler warning.</li>
 * </ul>
 */
final class ClauseTranslator {

    private final Messager messager;

    ClauseTranslator(Messager messager) {
        this.messager = messager;
    }

    /**
     * A wrapped contract clause: the argument expression of a
     * {@code precondition} or {@code postcondition} call.
     */
    static final class Clause {
        final JCTree.JCExpression expression;

        Clause(JCTree.JCExpression expression) {
            this.expression = expression;
        }
    }

    /** Result of translating one or more postconditions. */
    static final class PostconditionResult {
        /** {@code T r = method(args);} or {@code method(args);} if no binding. */
        final JCTree.JCStatement callBinding;
        /** {@code return Q1 && Q2 && ... ;} */
        final JCTree.JCStatement returnStatement;

        PostconditionResult(JCTree.JCStatement callBinding, JCTree.JCStatement returnStatement) {
            this.callBinding = callBinding;
            this.returnStatement = returnStatement;
        }
    }

    /**
     * Translate {@code precondition(P)} to {@code Assume.that(P);}.
     * Returns null if the clause is non-executable (e.g. quantifier).
     */
    JCTree.JCStatement translatePrecondition(Clause clause,
                                             TreeMaker maker,
                                             Names names,
                                             TreeCopier<Void> copier) {
        if (isNonExecutable(clause.expression)) {
            messager.printMessage(Diagnostic.Kind.NOTE,
                "skipping non-executable precondition: " + clause.expression);
            return null;
        }
        JCTree.JCExpression assumeRef = qualifiedIdent(maker, names,
            "net", "jqwik", "api", "Assume", "that");
        JCTree.JCMethodInvocation call = maker.Apply(
            List.nil(),
            assumeRef,
            List.of(copier.copy(clause.expression))
        );
        return maker.Exec(call);
    }

    /**
     * Translate one or more {@code postcondition(...)} calls into a call binding
     * (when at least one clause uses a return-binding lambda) and a single
     * return statement that conjoins the bodies with {@code &&}.
     *
     * <p>Returns null if the postconditions cannot be translated (e.g. all are
     * non-executable, or lambda parameter names disagree).</p>
     */
    PostconditionResult translatePostconditions(List<Clause> clauses,
                                                JCTree.JCMethodDecl original,
                                                TreeMaker maker,
                                                Names names,
                                                TreeCopier<Void> copier) {
        if (clauses.isEmpty()) {
            return null;
        }

        // Collect translated bodies and the binding name (if any).
        ListBuffer<JCTree.JCExpression> bodies = new ListBuffer<>();
        Name returnVarName = null;

        for (Clause clause : clauses) {
            JCTree.JCExpression body;
            Name clauseBinding = null;

            if (clause.expression instanceof JCTree.JCLambda lambda) {
                if (lambda.params.size() != 1) {
                    messager.printMessage(Diagnostic.Kind.WARNING,
                        "postcondition lambda must have exactly one parameter; got " + lambda.params.size());
                    continue;
                }
                clauseBinding = lambda.params.head.name;
                if (lambda.body instanceof JCTree.JCExpression expr) {
                    body = expr;
                } else if (lambda.body instanceof JCTree.JCBlock block && block.stats.size() == 1
                        && block.stats.head instanceof JCTree.JCReturn ret) {
                    body = ret.expr;
                } else {
                    messager.printMessage(Diagnostic.Kind.WARNING,
                        "postcondition lambda body must be an expression or a single return statement");
                    continue;
                }
            } else {
                // postcondition(boolExpr) — no return binding for this clause.
                body = clause.expression;
            }

            if (isNonExecutable(body)) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                    "skipping non-executable postcondition: " + body);
                continue;
            }

            // All lambda postconditions in a method must agree on the binding name.
            if (clauseBinding != null) {
                if (returnVarName == null) {
                    returnVarName = clauseBinding;
                } else if (!returnVarName.contentEquals(clauseBinding.toString())) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                        "postconditions in the same @AsProperty method must use the same return-binding name; "
                            + "found '" + returnVarName + "' and '" + clauseBinding + "'");
                    return null;
                }
            }

            bodies.append(copier.copy(body));
        }

        if (bodies.isEmpty()) {
            // All clauses were non-executable.
            return null;
        }

        // Build the call: method(p1, p2, ...)
        ListBuffer<JCTree.JCExpression> argExprs = new ListBuffer<>();
        for (JCTree.JCVariableDecl param : original.params) {
            argExprs.append(maker.Ident(param.name));
        }
        JCTree.JCMethodInvocation call = maker.Apply(
            List.nil(),
            maker.Ident(original.name),
            argExprs.toList()
        );

        JCTree.JCStatement callBinding;
        if (returnVarName != null) {
            // The return type of the original method is the type of the binding.
            JCTree.JCExpression varType = copier.copy(original.restype);
            callBinding = maker.VarDef(
                maker.Modifiers(0),
                returnVarName,
                varType,
                call
            );
        } else {
            // No clause referenced the result. Invoke the method as a statement
            // so it still runs (and any preconditions on the call site are
            // exercised), then assert the conjunction over the parameters.
            callBinding = maker.Exec(call);
        }

        JCTree.JCExpression conjunction = foldAnd(bodies.toList(), maker);
        JCTree.JCReturn ret = maker.Return(conjunction);
        return new PostconditionResult(callBinding, ret);
    }

    /**
     * Fold a non-empty list of expressions with the binary {@code &&} operator,
     * left-associatively: {@code [a, b, c]} → {@code (a && b) && c}.
     */
    private JCTree.JCExpression foldAnd(List<JCTree.JCExpression> exprs, TreeMaker maker) {
        JCTree.JCExpression result = exprs.head;
        for (JCTree.JCExpression next : exprs.tail) {
            result = maker.Binary(JCTree.Tag.AND, result, next);
        }
        return result;
    }

    /**
     * Returns true if the expression contains a construct that has no executable
     * runtime semantics: quantifiers ({@code forall} / {@code exists}) or
     * {@code old(...)}.
     *
     * <p>The check is a conservative textual scan — sufficient for the common
     * cases where a quantifier or {@code old} appears as a method call inside
     * the clause expression.</p>
     */
    private boolean isNonExecutable(JCTree.JCExpression expr) {
        String text = expr.toString();
        return text.contains("forall(") || text.contains("exists(") || text.contains("old(");
    }

    private JCTree.JCExpression qualifiedIdent(TreeMaker maker, Names names, String... parts) {
        JCTree.JCExpression e = maker.Ident(names.fromString(parts[0]));
        for (int i = 1; i < parts.length; i++) {
            e = maker.Select(e, names.fromString(parts[i]));
        }
        return e;
    }
}
