package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.CatchClause;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.Variable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Structural utilities over Laurel trees for the bytecode translator:
 *
 * <ul>
 *   <li>{@link #rerange}: the translator builds expressions bottom-up
 *       (operand stack) before the owning statement is known, so at statement
 *       placement the subtree is rebuilt with the statement's range —
 *       stopping at already-placed statement nodes, which keep their own
 *       ranges (identity-based stop set).</li>
 *   <li>{@link #structuralEquals}: structural node comparison via
 *       {@code toString()} with ranges excluded through canonicalization.</li>
 *   <li>{@link #negate}: logical negation of a condition — flips
 *       {@code $eq/$neq/$lt/$le/$gt/$ge} StaticCalls, honoring the
 *       NaN-unordered marker (float/double relationals must not flip), and
 *       unwraps/wraps {@code $not}.</li>
 *   <li>{@link #isBooleanish}: structural "this is a boolean" test over
 *       Laurel shapes.</li>
 * </ul>
 */
final class LaurelTrees {

    private LaurelTrees() {
    }

    // --- Rerange: rebuild a subtree with a new range on every node ---

    /**
     * Rebuild {@code node}'s subtree with every {@link AstNode} carrying
     * {@code range}, stopping at nodes contained in {@code stop} (which must
     * be an identity-based set): those keep their subtree untouched. HighType
     * nodes are left as-is (they always carry UNKNOWN_RANGE).
     */
    static AstNode<StmtExpr> rerange(AstNode<StmtExpr> node, FileRange range, Set<AstNode<StmtExpr>> stop) {
        if (stop.contains(node)) {
            return node;
        }
        return new AstNode<>(rerangeVal(node.val(), range, stop), range);
    }

    private static Optional<AstNode<StmtExpr>> rerange(Optional<AstNode<StmtExpr>> node, FileRange range,
                                                       Set<AstNode<StmtExpr>> stop) {
        return node.map(n -> rerange(n, range, stop));
    }

    private static List<AstNode<StmtExpr>> rerangeAll(List<AstNode<StmtExpr>> nodes, FileRange range,
                                                      Set<AstNode<StmtExpr>> stop) {
        var out = new ArrayList<AstNode<StmtExpr>>(nodes.size());
        for (var n : nodes) {
            out.add(rerange(n, range, stop));
        }
        return out;
    }

    static AstNode<Variable> rerangeVar(AstNode<Variable> node, FileRange range, Set<AstNode<StmtExpr>> stop) {
        Variable v = switch (node.val()) {
            case Variable.Local l -> l;
            case Variable.Declare d -> d;
            case Variable.Field f -> new Variable.Field(rerange(f.target(), range, stop), f.fieldName());
        };
        return new AstNode<>(v, range);
    }

    private static List<AstNode<Variable>> rerangeVars(List<AstNode<Variable>> nodes, FileRange range,
                                                       Set<AstNode<StmtExpr>> stop) {
        var out = new ArrayList<AstNode<Variable>>(nodes.size());
        for (var n : nodes) {
            out.add(rerangeVar(n, range, stop));
        }
        return out;
    }

    private static StmtExpr rerangeVal(StmtExpr e, FileRange range, Set<AstNode<StmtExpr>> stop) {
        return switch (e) {
            case StmtExpr.IfThenElse n -> new StmtExpr.IfThenElse(rerange(n.cond(), range, stop),
                    rerange(n.thenBranch(), range, stop), rerange(n.elseBranch(), range, stop));
            case StmtExpr.Block n -> new StmtExpr.Block(rerangeAll(n.statements(), range, stop), n.label());
            case StmtExpr.While n -> new StmtExpr.While(rerange(n.cond(), range, stop),
                    rerangeAll(n.invariants(), range, stop), rerange(n.decreases(), range, stop),
                    rerange(n.body(), range, stop), n.postTest());
            case StmtExpr.Exit n -> n;
            case StmtExpr.Return n -> new StmtExpr.Return(rerange(n.value(), range, stop));
            case StmtExpr.LiteralInt n -> n;
            case StmtExpr.LiteralBool n -> n;
            case StmtExpr.LiteralString n -> n;
            case StmtExpr.LiteralDecimal n -> n;
            case StmtExpr.LiteralBv n -> n;
            case StmtExpr.Var n -> new StmtExpr.Var(switch (n.var_()) {
                case Variable.Local l -> l;
                case Variable.Declare d -> d;
                case Variable.Field f -> new Variable.Field(rerange(f.target(), range, stop), f.fieldName());
            });
            case StmtExpr.Assign n -> new StmtExpr.Assign(rerangeVars(n.targets(), range, stop),
                    rerange(n.value(), range, stop));
            case StmtExpr.IncrDecr n -> new StmtExpr.IncrDecr(n.mode(), n.op(),
                    rerangeVar(n.target(), range, stop));
            case StmtExpr.CompoundAssign n -> new StmtExpr.CompoundAssign(n.op(),
                    rerangeVar(n.target(), range, stop), rerange(n.rhs(), range, stop));
            case StmtExpr.PureFieldUpdate n -> new StmtExpr.PureFieldUpdate(rerange(n.target(), range, stop),
                    n.fieldName(), rerange(n.newValue(), range, stop));
            case StmtExpr.StaticCall n -> new StmtExpr.StaticCall(n.callee(),
                    rerangeAll(n.arguments(), range, stop), n.typeArgs());
            case StmtExpr.New n -> n;
            case StmtExpr.This n -> n;
            case StmtExpr.ReferenceEquals n -> new StmtExpr.ReferenceEquals(rerange(n.lhs(), range, stop),
                    rerange(n.rhs(), range, stop));
            case StmtExpr.AsType n -> new StmtExpr.AsType(rerange(n.target(), range, stop), n.targetType());
            case StmtExpr.IsType n -> new StmtExpr.IsType(rerange(n.target(), range, stop), n.type());
            case StmtExpr.InstanceCall n -> new StmtExpr.InstanceCall(rerange(n.target(), range, stop),
                    n.callee(), rerangeAll(n.arguments(), range, stop));
            case StmtExpr.Quantifier n -> new StmtExpr.Quantifier(n.mode(), n.param(),
                    rerange(n.trigger(), range, stop), rerange(n.body(), range, stop));
            case StmtExpr.Assigned n -> new StmtExpr.Assigned(rerange(n.name(), range, stop));
            case StmtExpr.Old n -> new StmtExpr.Old(rerange(n.value(), range, stop));
            case StmtExpr.Fresh n -> new StmtExpr.Fresh(rerange(n.value(), range, stop));
            case StmtExpr.Assert n -> new StmtExpr.Assert(rerange(n.condition(), range, stop), n.summary());
            case StmtExpr.Assume n -> new StmtExpr.Assume(rerange(n.condition(), range, stop));
            case StmtExpr.Throw n -> new StmtExpr.Throw(rerange(n.value(), range, stop));
            case StmtExpr.Try n -> {
                var catches = new ArrayList<CatchClause>(n.catches().size());
                for (var c : n.catches()) {
                    catches.add(new CatchClause(c.binding(), rerange(c.predicate(), range, stop),
                            rerange(c.body(), range, stop), c.bindingType()));
                }
                yield new StmtExpr.Try(rerange(n.body(), range, stop), catches,
                        rerange(n.finally_(), range, stop));
            }
            case StmtExpr.ProveBy n -> new StmtExpr.ProveBy(rerange(n.value(), range, stop),
                    rerange(n.proof(), range, stop));
            case StmtExpr.ContractOf n -> new StmtExpr.ContractOf(n.type(), rerange(n.function(), range, stop));
            case StmtExpr.Abstract n -> n;
            case StmtExpr.All n -> n;
            case StmtExpr.Hole n -> n;
        };
    }

    // --- Structural equality modulo ranges ---

    private static final Set<AstNode<StmtExpr>> NO_STOP = Set.of();

    /**
     * Structural equality of two expressions ignoring source ranges.
     * Identifier ranges are uniform (always UNKNOWN_RANGE from the factory),
     * so canonicalizing the AstNode ranges makes record equality exact.
     */
    static boolean structuralEquals(AstNode<StmtExpr> a, AstNode<StmtExpr> b) {
        if (a == b) {
            return true;
        }
        return rerange(a, SourceMap.UNKNOWN_RANGE, NO_STOP)
                .equals(rerange(b, SourceMap.UNKNOWN_RANGE, NO_STOP));
    }

    // --- Negation over $op StaticCalls ---

    /** The flipped relational/equality proc name, or null when not flippable. */
    private static String flippedProc(String proc, boolean unordered) {
        return switch (proc) {
            case "$eq" -> "$neq";      // equality flips safely even on NaN
            case "$neq" -> "$eq";
            case "$lt" -> unordered ? null : "$ge";
            case "$ge" -> unordered ? null : "$lt";
            case "$gt" -> unordered ? null : "$le";
            case "$le" -> unordered ? null : "$gt";
            default -> null;
        };
    }

    /**
     * Logical negation of a condition: a
     * {@code $not} unwraps; a flippable comparison flips its operator —
     * unless the NaN-unordered marker forbids it (float/double relationals:
     * {@code !(x < 0.0)} holds for NaN while {@code x >= 0.0} does not) —
     * anything else wraps in {@code $not}. {@code unordered} is an
     * identity-based set of comparison values marked by the translator.
     */
    static AstNode<StmtExpr> negate(AstNode<StmtExpr> e, Set<StmtExpr> unordered) {
        if (e.val() instanceof StmtExpr.StaticCall call) {
            String proc = call.callee().text();
            if (proc.equals("$not") && call.arguments().size() == 1) {
                return call.arguments().get(0);
            }
            if (call.arguments().size() == 2) {
                String flipped = flippedProc(proc, unordered.contains(e.val()));
                if (flipped != null) {
                    return new AstNode<>(new StmtExpr.StaticCall(
                            LaurelFactory.reservedIdent(flipped), call.arguments()), e.source());
                }
            }
        }
        return new AstNode<>(new StmtExpr.StaticCall(LaurelFactory.reservedIdent("$not"), List.of(e)), e.source());
    }

    // --- Boolean shape test ---

    /** Proc names whose result is boolean regardless of operand types. */
    private static boolean booleanProc(String proc) {
        return switch (proc) {
            case "$eq", "$neq", "$lt", "$gt", "$le", "$ge",
                 "$and", "$or", "$not", "$implies", "$andThen", "$orElse" -> true;
            default -> false;
        };
    }

    /**
     * Structural "this is a boolean" test over Laurel shapes: type tests,
     * boolean literals, {@code $not}, and the boolean-valued {@code $op}
     * calls. Bitwise-on-booleans never arises here: bitwise operators are
     * refused before reaching Laurel.
     */
    static boolean isBooleanish(StmtExpr e) {
        return switch (e) {
            case StmtExpr.IsType t -> true;
            case StmtExpr.LiteralBool b -> true;
            case StmtExpr.StaticCall c -> booleanProc(c.callee().text());
            default -> false;
        };
    }

    // --- Small shape helpers ---

    static boolean isTrueLit(StmtExpr e) {
        return e instanceof StmtExpr.LiteralBool b && b.value();
    }

    static boolean isFalseLit(StmtExpr e) {
        return e instanceof StmtExpr.LiteralBool b && !b.value();
    }

    static boolean isZeroLit(StmtExpr e) {
        return e instanceof StmtExpr.LiteralInt i && i.value() == 0;
    }

    /** The {@code $op} StaticCall's proc name when {@code e} is one, else null. */
    static String staticCallName(StmtExpr e) {
        return e instanceof StmtExpr.StaticCall c ? c.callee().text() : null;
    }
}
