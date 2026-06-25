package org.strata.jverify.verifier.compiler.frontend;

import com.sun.source.tree.CaseTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Names;

import java.util.Optional;

/// Lowers Java switch statements and switch expressions into chained
/// if-statements and ?: conditionals respectively. Runs as its own phase in
/// JavaLowerer's pipeline before LOWER, because LOWER would otherwise mangle
/// switches into bytecode-flavored fall-through shapes JavaToLaurelCompiler
/// doesn't accept.
///
/// JavaToLaurelCompiler consumes the if/conditional shape directly, so the
/// rewrite is one-way (no UNSUSPEND-style reversal). Forms that can't be
/// encoded (case-statement groups, non-literal labels, pattern guards,
/// block/throw bodies in switch expressions, `case null`) fall through to
/// LOWER and surface as method-level skips via JavaToLaurelCompiler's
/// per-method catch. Step 2b of PLAN.md §3 will replace those skips with
/// per-construct refusal diagnostics emitted at this phase.
public class SwitchDesugarer extends TreeTranslator {

    private final TreeMaker maker;
    private final Names names;
    private final Symtab symtab;
    private int nextSelectorIndex = 0;

    public SwitchDesugarer(Context context) {
        this.maker = TreeMaker.instance(context);
        this.names = Names.instance(context);
        this.symtab = Symtab.instance(context);
    }

    public java.util.List<JCTree.JCCompilationUnit> transform(java.util.List<JCTree.JCCompilationUnit> envs) {
        for (var env : envs) {
            translate(env);
        }
        return envs;
    }

    @Override
    public void visitSwitch(JCTree.JCSwitch tree) {
        var maybeIf = switchToIf(tree.selector, tree.cases, tree.isExhaustive);
        if (maybeIf.isPresent()) {
            result = maybeIf.get();
            result.accept(this);
            return;
        }
        super.visitSwitch(tree);
    }

    @Override
    public void visitSwitchExpression(JCTree.JCSwitchExpression tree) {
        var maybeSwitch = switchExpressionToConditional(tree.selector, tree.cases, tree.type);
        if (maybeSwitch.isPresent()) {
            result = maybeSwitch.get();
            result.accept(this);
            return;
        }
        super.visitSwitchExpression(tree);
    }

    ///
    /// Translates a switch statement into an equivalent chained if statement.
    ///
    /// For example:
    /// {@snippet :
    ///       var num = -1;
    ///       switch (i) {
    ///            case 0 -> num = 10;
    ///            case 1, 2 -> num = 20;
    ///            case 3 -> num = 30;
    ///            default -> num = 40;
    ///       }
    ///  }
    /// Becomes:
    /// {@snippet :
    ///       var num = -1;
    ///       if (i == 0) {
    ///            num = 10;
    ///       } else if (i == 1 || i == 2) {
    ///            num = 20;
    ///       } else if (i == 3) {
    ///            num = 30;
    ///       } else {
    ///            num = 40;
    ///       }
    ///  }
    /// If the switch was determined to be exhaustive and there is no default case label,
    /// then an extra `else { assert false; } branch is added at the end to encode that bit.
    ///
    /// Arrow-form Java allows the `default` case in any position, but the
    /// generated if-cascade has to put it last to preserve semantics — a
    /// `default` mid-cascade lowers to `else if (true) { ... }` which makes
    /// every later `else if` dead. Partition before recursing.
    ///
    /// Returns an Optional<JCStatement>. Reaching Optional.empty() means
    /// caseLabelToExpression rejected an unknown JCCaseLabel subclass — a
    /// future Java version added a label kind. Defensive: skip desugaring,
    /// LOWER may then mutate the tree unpredictably, but the user will see
    /// errors.
    ///
    private Optional<JCTree.JCStatement> switchToIf(JCTree.JCExpression selector, List<JCTree.JCCase> cases, boolean exhaustive) {
        // Refuse STATEMENT-form switches uniformly. caseToExpression already
        // refuses STATEMENT-form non-default cases; extending the check here
        // also covers a STATEMENT-form default — wrapping its body in a
        // plain Block would orphan any `break` it contains, retargeting
        // them to the enclosing loop instead of the (now-removed) switch.
        for (var cas : cases) {
            if (cas.caseKind == CaseTree.CaseKind.STATEMENT) {
                return Optional.empty();
            }
        }
        Optional<JCTree.JCCase> defaultCase = Optional.empty();
        ListBuffer<JCTree.JCCase> nonDefault = new ListBuffer<>();
        for (var cas : cases) {
            if (cas.labels.size() == 1 && cas.labels.head instanceof JCTree.JCDefaultCaseLabel) {
                defaultCase = Optional.of(cas);
            } else {
                nonDefault.append(cas);
            }
        }
        // Hoist the selector into a fresh local so it's evaluated exactly
        // once. Without this, side-effecting selectors (e.g. method calls
        // that mutate state) would be evaluated by every generated equality
        // test, costing precision in verification.
        var selSymbol = freshSelectorSymbol(selector.type);
        maker.pos = selector.pos;
        var selVarDecl = maker.VarDef(selSymbol, selector);
        var selRef = identFor(selSymbol);

        final JCTree.JCStatement tail;
        if (defaultCase.isPresent()) {
            tail = maker.Block(0, defaultCase.get().stats);
        } else if (exhaustive) {
            tail = maker.Assert(maker.Literal(false), null);
        } else {
            tail = null;
        }
        // Build the if-cascade from the inside out: start with `tail` as the
        // innermost else and prepend each non-default case in reverse.
        // `null` tail is preserved as a missing-else (Java allows that).
        JCTree.JCStatement chain = tail;
        var reversed = nonDefault.toList().reverse();
        for (var cas : reversed) {
            var maybeHead = caseToExpression(selRef, cas, cas.labels);
            if (maybeHead.isEmpty()) {
                return Optional.empty();
            }
            maker.pos = maybeHead.get().pos;
            chain = maker.If(maybeHead.get(), maker.Block(0, cas.stats), chain);
        }
        if (chain == null) {
            return Optional.empty();
        }
        return Optional.of(maker.Block(0, List.of(selVarDecl, chain)));
    }

    private Symbol.VarSymbol freshSelectorSymbol(Type type) {
        var name = names.fromString("$switchSelector" + (nextSelectorIndex++));
        return new Symbol.VarSymbol(0, name, type, symtab.noSymbol);
    }

    private JCTree.JCIdent identFor(Symbol.VarSymbol sym) {
        var ident = maker.Ident(sym);
        ident.type = sym.type;
        return ident;
    }

    private Optional<JCTree.JCExpression> caseLabelToExpression(JCTree.JCExpression selector, JCTree.JCCaseLabel label) {
        return switch (label) {
            case JCTree.JCConstantCaseLabel constantCaseLabel -> {
                var methodType = new Type.MethodType(List.of(selector.type, constantCaseLabel.expr.type), symtab.booleanType, null, null);
                yield Optional.of(makeResolvedBinary(JCTree.Tag.EQ, selector, constantCaseLabel.expr, methodType));
            }
            case JCTree.JCDefaultCaseLabel _ -> Optional.of(maker.Literal(true));
            default -> Optional.empty();
        };
    }

    private Optional<JCTree.JCExpression> caseToExpression(JCTree.JCExpression selector, JCTree.JCCase cas, List<JCTree.JCCaseLabel> labels) {
        if (cas.caseKind == CaseTree.CaseKind.STATEMENT) {
            return Optional.empty();
        } else if (labels.tail.isEmpty()) {
            return caseLabelToExpression(selector, labels.head);
        } else {
            return caseToExpression(selector, cas, labels.tail).flatMap(rest ->
                caseLabelToExpression(selector, labels.head).map(head ->
                    makeResolvedBinary(JCTree.Tag.OR, head, rest,
                            new Type.MethodType(List.of(symtab.booleanType, symtab.booleanType), symtab.booleanType, null, null))));
        }
    }

    private JCTree.JCBinary makeResolvedBinary(JCTree.Tag opcode, JCTree.JCExpression lhs, JCTree.JCExpression rhs, Type.MethodType resultType) {
        var result = maker.Binary(opcode, lhs, rhs);
        result.operator = new Symbol.OperatorSymbol(names.equals, resultType, -1, symtab.noSymbol);
        result.type = resultType.restype;
        return result;
    }

    ///
    /// Translates a switch expression into an equivalent chained conditional expression.
    ///
    /// For example:
    /// {@snippet :
    ///       var num = switch (i) {
    ///            case 0 -> 10;
    ///            case 1, 2 -> 20;
    ///            case 3 -> 30;
    ///            default -> 40;
    ///       };
    ///  }
    /// Becomes:
    /// {@snippet :
    ///       var num = (i == 0) ? 10 :
    ///            (i == 1 || i == 2) ? 20 :
    ///                 (i == 3) ? 30 :
    ///                      40;
    ///  }
    ///
    /// Returns an Optional<JCExpression>. Reaching Optional.empty() means
    /// caseLabelToExpression rejected an unknown JCCaseLabel subclass, or
    /// the default case had a non-expression body — defensive: skip
    /// desugaring, LOWER may then mutate the tree unpredictably, but the
    /// user will see errors.
    ///
    private Optional<JCTree.JCExpression> switchExpressionToConditional(JCTree.JCExpression selector, List<JCTree.JCCase> cases, Type typ) {
        Optional<JCTree.JCExpression> defaultBody = Optional.empty();
        ListBuffer<JCTree.JCCase> nonDefault = new ListBuffer<>();
        for (var cas : cases) {
            if (cas.labels.size() == 1 && cas.labels.head instanceof JCTree.JCDefaultCaseLabel) {
                if (!(cas.body instanceof JCTree.JCExpression body)) {
                    return Optional.empty();
                }
                defaultBody = Optional.of(body);
            } else {
                nonDefault.append(cas);
            }
        }
        // Switch expressions without a default are exhaustive over a known
        // domain (enum, sealed). javac enforces this before we run, so this
        // path is unreachable in practice — refuse to desugar defensively
        // rather than synthesizing a falsepart of arbitrary type.
        if (defaultBody.isEmpty()) {
            return Optional.empty();
        }
        // Hoist the selector into a fresh local so it's evaluated exactly
        // once — same motivation as switchToIf. Wrap the conditional chain
        // in a synthetic LetExpr; JavaToLaurelCompiler lowers it to a
        // Laurel block (which is a StmtExpr — usable in expression position).
        var selSymbol = freshSelectorSymbol(selector.type);
        maker.pos = selector.pos;
        var selVarDecl = maker.VarDef(selSymbol, selector);
        var selRef = identFor(selSymbol);
        return wrapCases(selRef, nonDefault.toList(), defaultBody.get(), typ)
                .map(expr -> {
                    var let = maker.LetExpr(selVarDecl, expr);
                    let.type = typ;
                    return let;
                });
    }

    private Optional<JCTree.JCExpression> wrapCases(JCTree.JCExpression selector, List<JCTree.JCCase> cases, JCTree.JCExpression defaultBody, Type typ) {
        if (cases.isEmpty()) {
            return Optional.of(defaultBody);
        }
        if (!(cases.head.body instanceof JCTree.JCExpression headExpr)) {
            return Optional.empty();
        }
        var rest = wrapCases(selector, cases.tail, defaultBody, typ);
        if (rest.isEmpty()) {
            return rest;
        }
        return caseToExpression(selector, cases.head, cases.head.labels).map(head -> {
            var conditional = maker.Conditional(head, headExpr, rest.get());
            conditional.type = typ;
            return conditional;
        });
    }
}
