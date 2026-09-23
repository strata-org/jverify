package org.strata.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;

import org.strata.jverify.verifier.compiler.Reporter;

/**
 * Enforces the structural rules a {@code @Pure} method body must obey, as errors,
 * before Laurel generation runs.
 *
 * <p>A {@code @Pure} method becomes a Laurel FUNCTION, so its body has to be an
 * expression. JVerify accepts a restricted statement form that reads as one -- a
 * run of variable declarations ending in a {@code return}, with {@code if}/
 * {@code else} for choice -- and everything outside that shape has no expression
 * translation. These checks are what make the restriction visible to the user at
 * the offending construct rather than as a refusal naming only the method.
 *
 * <p>Like {@link UnsupportedConstructValidator} these used to live in the deleted
 * Dafny generator, and their message keys survived in {@code messages.properties}
 * ({@code pureMethodsNeedsReturnType}, {@code pureMethodLastStatement},
 * {@code pureMethodEndingIfThen}, {@code pureBlockNotLastMustBeVariableDeclaration},
 * {@code pureAssignmentNeedsInitializer}, {@code mutatingExpression}); only the
 * call sites were lost.
 *
 * <p>The rules, as the fixtures pin them ({@code PureMethodErrors},
 * {@code ImpureExpressionsResolution}):
 * <ul>
 * <li>The method must return something -- a {@code void} {@code @Pure} method has
 * no value to be.
 * <li>A "pure block" ends in a {@code return}, or in an {@code if} WITH an
 * {@code else} (whose branches are themselves pure blocks). An {@code if} without
 * an {@code else} has no value on the missing path; an empty block has no value at
 * all.
 * <li>Before the last statement, a bare EXPRESSION statement (a call) is
 * rejected: it can only be there for its effect. An {@code if} is control flow, so
 * it is RECURSED INTO rather than rejected -- which is why
 * {@code PureMethodErrors.pureEndingWithIfNotElse} reports only its inner
 * {@code if} and not the outer one. Loops, blocks, {@code try}, {@code switch} and
 * {@code throw} are deliberately NOT rejected; see the note in
 * {@code checkPureBlock}.
 * <li>Every declaration needs an initializer: a declaration with no value cannot
 * become a {@code let}.
 * <li>No mutating expression anywhere, including inside an {@code if} condition
 * ({@code ImpureExpressionsResolution} puts {@code x += 1} and {@code x++} there).
 * <li>No user-written {@code modifies(...)} clause; see {@link #checkNoModifiesClause}.
 * </ul>
 *
 * <p>This pass runs BEFORE {@link MethodOrLoopContractCompiler}, which lifts the
 * contract header into a nested block -- so a contract call is still a plain
 * statement of {@code tree.body} here, which both {@link #checkPureBlock} and
 * {@link #checkNoModifiesClause} rely on.
 */
public class PureMethodValidator extends TreeScanner {

    private final Reporter reporter;
    private final JVerifyUtils jverifyUtils;

    public PureMethodValidator(Context context) {
        this.reporter = Reporter.instance(context);
        this.jverifyUtils = JVerifyUtils.instance(context);
    }

    /** Validate one compilation unit. Contract sources are the caller's to exclude. */
    public void validate(JCTree.JCCompilationUnit unit) {
        reporter.compilationUnit = unit;
        scan(unit);
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        if (tree.sym != null && jverifyUtils.isPure(tree.sym) && !isNotVerified(tree.sym)) {
            if (tree.restype != null
                    && tree.restype.type != null
                    && tree.restype.type.getKind() == javax.lang.model.type.TypeKind.VOID) {
                reporter.reportError(tree, "pureMethodsNeedsReturnType");
            }
            if (tree.body != null) {
                new MutationScanner().scan(tree.body);
                checkNoModifiesClause(tree.body);
                checkPureBlock(tree.body);
            }
        }
        super.visitMethodDef(tree);
    }

    /**
     * True for a method opted out of verification with {@code @Verify(false)}. Its body
     * is a stub -- typically a bare {@code throw} -- so the pure SHAPE rules, which
     * exist to make the body translatable to an expression, do not apply to it.
     */
    private boolean isNotVerified(com.sun.tools.javac.code.Symbol.MethodSymbol symbol) {
        return VerifyAnnotationCompiler.isOptedOutOfVerification(
                symbol, jverifyUtils.getVerifyClassSymbol());
    }

    /**
     * Check one pure block. Reports at most one shape error for the block itself,
     * plus whatever its declarations and nested blocks contribute.
     */
    private void checkPureBlock(JCTree.JCBlock block) {
        var statements = new java.util.ArrayList<JCTree.JCStatement>();
        for (JCTree.JCStatement statement : block.stats) {
            if (!isContractHeaderCall(statement)) {
                statements.add(statement);
            }
        }
        if (statements.isEmpty()) {
            // No value at all. Anchored at the block's own `{`, which is the only
            // position there is.
            reporter.reportError(block, "pureMethodLastStatement");
            return;
        }
        for (int i = 0; i < statements.size(); i++) {
            JCTree.JCStatement statement = statements.get(i);
            boolean last = i == statements.size() - 1;
            if (statement instanceof JCTree.JCVariableDecl declaration) {
                if (declaration.init == null) {
                    reporter.reportError(declaration, "pureAssignmentNeedsInitializer",
                            declaration.name.toString());
                }
                if (last) {
                    reporter.reportError(declaration, "pureMethodLastStatement");
                }
            } else if (statement instanceof JCTree.JCIf conditional) {
                checkPureBranch(conditional.thenpart);
                if (conditional.elsepart != null) {
                    checkPureBranch(conditional.elsepart);
                } else if (last) {
                    reporter.reportError(conditional, "pureMethodEndingIfThen");
                }
            } else if (statement instanceof JCTree.JCExpressionStatement) {
                if (last) {
                    reporter.reportError(statement, "pureMethodLastStatement");
                } else {
                    // Anchored at the statement's START, not its preferred position,
                    // so a call reads under its name rather than under its '('.
                    reporter.reportError(
                            new JCDiagnostic.SimpleDiagnosticPosition(statement.getStartPosition()),
                            "pureBlockNotLastMustBeVariableDeclaration");
                }
            }
            // Everything else -- a loop, a bare block, try, switch, throw -- is left
            // alone DELIBERATELY. These checks come from the deleted Dafny back-end,
            // whose notion of a "pure block" was narrower than what the pipeline
            // accepts today: a @Pure body may contain a loop (that combination has its
            // own diagnostic, see PureLoopWithPostcondition) and a @Verify(false) stub
            // may be a bare `throw`. Rejecting those here would trade working programs
            // for a restored diagnostic, so only the shapes the error fixtures actually
            // pin are reported.
        }
    }

    /** A branch of a terminal if-else: a block is a pure block, a bare statement stands alone. */
    private void checkPureBranch(JCTree.JCStatement branch) {
        if (branch instanceof JCTree.JCBlock block) {
            checkPureBlock(block);
        } else if (branch instanceof JCTree.JCIf conditional) {
            checkPureBranch(conditional.thenpart);
            if (conditional.elsepart != null) {
                checkPureBranch(conditional.elsepart);
            } else {
                reporter.reportError(conditional, "pureMethodEndingIfThen");
            }
        } else if (!(branch instanceof JCTree.JCReturn)) {
            reporter.reportError(branch, "pureMethodLastStatement");
        }
    }

    /**
     * Reject a user-written {@code modifies(...)} clause on a {@code @Pure} method.
     *
     * <p>{@code @Pure} means the method translates to a TRANSPARENT Laurel body, and
     * {@code Body.Transparent} structurally cannot carry a frame -- which is why
     * {@code JavaToLaurelCompiler}'s {@code canStayTransparent} requires
     * {@code modifies.isEmpty()}. So the clause is not inert: unchecked, it silently
     * demotes the method to an opaque procedure, and the user gets a different
     * translation instead of a diagnostic.
     *
     * <p>Only the method's own clause. A {@code throwsOn(...).modifies(...)} chain
     * ends in {@code JVerify.ThrowsOnCase.modifies}, whose direct owner is the nested
     * class rather than {@code JVerify}, so the owner test below excludes it -- the
     * same way {@code UnsupportedConstructValidator.isHeaderContractCall} tells a
     * header clause from a case clause. A FE-SYNTHESIZED wildcard frame is not
     * affected either: that is added during Laurel generation, long after this pass,
     * and a {@code @Pure} method is allowed to write the heap.
     */
    private void checkNoModifiesClause(JCTree.JCBlock body) {
        for (JCTree.JCStatement statement : body.stats) {
            if (statement instanceof JCTree.JCExpressionStatement expressionStatement
                    && expressionStatement.expr instanceof JCTree.JCMethodInvocation invocation
                    && isModifiesClause(invocation)) {
                reporter.reportError(invocation, "wrongContract",
                        "modifies clauses", "pure method");
            }
        }
    }

    /** A {@code JVerify.modifies(...)} header clause, as opposed to any other call. */
    private static boolean isModifiesClause(JCTree.JCMethodInvocation invocation) {
        var method = JVerifyUtils.getJVerifyMethod(invocation);
        return method != null
                && method.owner.getQualifiedName().contentEquals(JVerifyUtils.JVERIFY_CLASS)
                && method.getQualifiedName().contentEquals("modifies");
    }

    /**
     * True for a statement that is a call to a JVerify contract method. Those are
     * the method's lifted contract header, not part of its value, so the shape rules
     * must not count them.
     */
    private boolean isContractHeaderCall(JCTree.JCStatement statement) {
        if (!(statement instanceof JCTree.JCExpressionStatement expressionStatement)
                || !(expressionStatement.expr instanceof JCTree.JCMethodInvocation invocation)) {
            return false;
        }
        var method = JVerifyUtils.getJVerifyMethod(invocation);
        return method != null;
    }

    /**
     * Reports mutating expressions that feed THIS {@code @Pure} method's value.
     * Compound assignment and increment/decrement only: a plain {@code =} to a local
     * is already rejected by the block shape (it is an expression statement, so it
     * cannot be a non-last statement), and treating it as a mutation here would
     * double-report.
     *
     * <p>Two scoping rules keep it from over-reporting:
     * <ul>
     * <li>A {@code for} loop's INIT and UPDATE clauses are skipped: a loop counter
     * ({@code for (int i = 0; i < n; i++)}) is a legitimate mutation, and a
     * {@code @Pure} body may contain a loop (see {@code checkPureBlock}'s note and
     * {@code PureLoopWithPostcondition}). Its condition and body are still scanned.
     * <li>Nested lambdas, anonymous/local classes and their methods are NOT
     * descended into: a mutation there belongs to a different scope, is not part of
     * this method's expression translation, and a nested {@code @Pure} method is
     * scanned by its own {@code visitMethodDef} run.
     * </ul>
     */
    private final class MutationScanner extends TreeScanner {
        @Override
        public void visitAssignop(JCTree.JCAssignOp tree) {
            reporter.reportError(tree, "mutatingExpression", operatorText(tree.getTag()));
            super.visitAssignop(tree);
        }

        @Override
        public void visitUnary(JCTree.JCUnary tree) {
            switch (tree.getTag()) {
                case PREINC, POSTINC -> reporter.reportError(tree, "mutatingExpression", "++");
                case PREDEC, POSTDEC -> reporter.reportError(tree, "mutatingExpression", "--");
                default -> { }
            }
            super.visitUnary(tree);
        }

        @Override
        public void visitForLoop(JCTree.JCForLoop tree) {
            // Skip init and step (loop-counter mutation is fine); scan cond + body.
            scan(tree.cond);
            scan(tree.body);
        }

        @Override
        public void visitLambda(JCTree.JCLambda tree) {
            // A separate scope; not this method's value.
        }

        @Override
        public void visitClassDef(JCTree.JCClassDecl tree) {
            // A nested/local/anonymous class's members have their own scope and, if
            // @Pure, their own MutationScanner run.
        }

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl tree) {
            // A local class's method: handled by the outer PureMethodValidator's own
            // visitMethodDef, not by this method's mutation scan.
        }

        private String operatorText(JCTree.Tag tag) {
            return switch (tag) {
                case PLUS_ASG -> "+=";
                case MINUS_ASG -> "-=";
                case MUL_ASG -> "*=";
                case DIV_ASG -> "/=";
                case MOD_ASG -> "%=";
                case BITAND_ASG -> "&=";
                case BITOR_ASG -> "|=";
                case BITXOR_ASG -> "^=";
                case SL_ASG -> "<<=";
                case SR_ASG -> ">>=";
                case USR_ASG -> ">>>=";
                default -> "=";
            };
        }
    }
}
