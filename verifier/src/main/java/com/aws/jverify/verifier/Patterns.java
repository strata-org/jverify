package com.aws.jverify.verifier;

import com.aws.jverify.generated.*;
import com.sun.tools.javac.tree.JCTree;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class Patterns {
    JavaToDafnyCompiler compiler;

    public Patterns(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
    }

    record SwitchLabelPatternAndBody(JCTree.JCCase cas, ExtendedPattern pattern, JCTree body) {}

    /**
     * Translates the switch labels of the given {@code switch} statement or expression
     * into the corresponding {@link ExtendedPattern}s,
     * and returns the patterns along with their corresponding (untranslated) bodies.
     * Returns {@code null} if an unrecoverable error is reported,
     * such as if the input tree uses unsupported features.
     */
    @Nullable
    List<SwitchLabelPatternAndBody> translateSwitchLabels(JCTree switchTree) {
        // JCTree is the first common superclass of JCSwitch and JCSwitchExpression,
        // so we settle for dynamically checking that the argument is one of them.
        var cases = switch (switchTree) {
            case JCTree.JCSwitch switchStmt -> switchStmt.getCases();
            case JCTree.JCSwitchExpression switchExpr -> switchExpr.getCases();
            default -> throw new IllegalArgumentException(
                    "Expected switch statement or expression but got " + switchTree.getClass());
        };

        // A switch block consists of either *switch rules* (label -> body)
        // or *switch labeled statement groups* (label: {label:} stmts).
        // Unlike switch rules, switch labeled statement groups automatically "fall through" without break statements,
        // but Dafny's match statement/expression can't express that easily.
        // So for now we only support switch blocks using switch rules.
        if (cases.getFirst().getCaseKind().equals(JCTree.JCCase.STATEMENT)) {
            compiler.reportError(switchTree, "notSupported", "switch labeled statement group");
            return null;
        }

        return cases.stream()
                .map(cas -> new SwitchLabelPatternAndBody(cas, translateSwitchLabel(cas), cas.getBody()))
                .toList();
    }

    /**
     * Translates the given switch label into a pattern.
     */
    private ExtendedPattern translateSwitchLabel(JCTree.JCCase cas) {
        // Each case has a *switch label*, which is either a *default label* or a *case label*.
        // A *case label* consists of either:
        //  - a list of *case constants*
        //  - a null literal (which the javac AST treats like another case constant)
        //  - a *case pattern* (not supported)

        // note: if the case label is a null literal, this is the singleton list of the null literal
        var caseConstants = cas.getExpressions();
        var defaultLabel = cas.getLabels().stream()
                .filter(label -> label instanceof JCTree.JCDefaultCaseLabel)
                .findFirst();

        if (caseConstants.nonEmpty()) {
            var literals = caseConstants.stream().map(this::translateCaseConstant).toList();
            return new DisjunctivePattern(compiler.toOrigin(cas), false, literals);
        } else if (defaultLabel.isPresent()) {
            return makeWildPattern(compiler.toOrigin(defaultLabel.get()));
        } else {
            compiler.reportError(cas, "notSupported", "case pattern");
            // Return something sensible
            return makeWildPattern(compiler.toOrigin(cas));
        }
    }

    /**
     * Translates the given switch label case constant into a pattern.
     */
    private ExtendedPattern translateCaseConstant(JCTree.JCExpression expr) {
        var origin = compiler.toOrigin(expr);
        final LiteralExpr litExpr;
        if (expr instanceof JCTree.JCLiteral) {
            litExpr = (LiteralExpr)compiler.expressionCompiler.toExpr(expr);
        } else {
            compiler.reportError(expr, "notSupported", "non-literal case constant");
            litExpr = JavaToDafnyCompiler.getHole(origin);
        }
        return new LitPattern(origin, false, litExpr);
    }

    public static IdPattern makeWildPattern(IOrigin origin) {
        return new IdPattern(origin, false, "_", null, null, false);
    }
}
