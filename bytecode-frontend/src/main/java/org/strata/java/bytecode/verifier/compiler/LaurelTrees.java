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
 *       {@code $eq/$neq/$lt/$le/$gt/$ge} static Calls, honoring the
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
            case Variable.Stack st -> st;
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
            case StmtExpr.Block n -> new StmtExpr.Block(rerangeAll(n.statements(), range, stop), n.label(),
                    rerangeAll(n.invariants(), range, stop), rerange(n.decreases(), range, stop));
            case StmtExpr.While n -> new StmtExpr.While(rerange(n.cond(), range, stop),
                    rerangeAll(n.invariants(), range, stop), rerange(n.decreases(), range, stop),
                    rerange(n.body(), range, stop), n.postTest());
            case StmtExpr.Return n -> new StmtExpr.Return(rerangeAll(n.values(), range, stop));
            case StmtExpr.Goto n -> n;
            case StmtExpr.LiteralInt n -> n;
            case StmtExpr.LiteralBool n -> n;
            case StmtExpr.LiteralString n -> n;
            case StmtExpr.LiteralDecimal n -> n;
            case StmtExpr.LiteralBv n -> n;
            case StmtExpr.Var n -> new StmtExpr.Var(switch (n.var_()) {
                case Variable.Local l -> l;
                case Variable.Declare d -> d;
                case Variable.Field f -> new Variable.Field(rerange(f.target(), range, stop), f.fieldName());
                case Variable.Stack st -> st;
            });
            case StmtExpr.Assign n -> new StmtExpr.Assign(rerangeVars(n.targets(), range, stop),
                    rerange(n.value(), range, stop));
            case StmtExpr.IncrDecr n -> new StmtExpr.IncrDecr(n.mode(), n.op(),
                    rerangeVar(n.target(), range, stop));
            case StmtExpr.CompoundAssign n -> new StmtExpr.CompoundAssign(n.op(),
                    rerangeVar(n.target(), range, stop), rerange(n.rhs(), range, stop));
            case StmtExpr.PureFieldUpdate n -> new StmtExpr.PureFieldUpdate(rerange(n.target(), range, stop),
                    n.fieldName(), rerange(n.newValue(), range, stop));
            case StmtExpr.Call n -> new StmtExpr.Call(rerange(n.receiver(), range, stop), n.callee(),
                    rerangeAll(n.arguments(), range, stop), n.typeArgs());
            case StmtExpr.New n -> n;
            case StmtExpr.This n -> n;
            case StmtExpr.ReferenceEquals n -> new StmtExpr.ReferenceEquals(rerange(n.lhs(), range, stop),
                    rerange(n.rhs(), range, stop));
            case StmtExpr.AsType n -> new StmtExpr.AsType(rerange(n.target(), range, stop), n.targetType());
            case StmtExpr.IsType n -> new StmtExpr.IsType(rerange(n.target(), range, stop), n.type());
            case StmtExpr.Quantifier n -> new StmtExpr.Quantifier(n.mode(), n.param(),
                    rerange(n.trigger(), range, stop), rerange(n.body(), range, stop));
            case StmtExpr.Assigned n -> new StmtExpr.Assigned(rerange(n.name(), range, stop));
            case StmtExpr.Old n -> new StmtExpr.Old(rerange(n.value(), range, stop), n.label());
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
            case StmtExpr.OldGuarantee n -> new StmtExpr.OldGuarantee(rerange(n.value(), range, stop));
            case StmtExpr.OldRelies n -> new StmtExpr.OldRelies(rerange(n.value(), range, stop));
            case StmtExpr.Yield n -> n;
            case StmtExpr.Resume n -> new StmtExpr.Resume(rerange(n.target(), range, stop),
                    rerange(n.value(), range, stop));
            case StmtExpr.HasNext n -> new StmtExpr.HasNext(rerange(n.target(), range, stop));
            case StmtExpr.Snapshot n -> n;
        };
    }

    // --- Post-order rewrite: rebuild children, then let `f` replace the node ---

    /**
     * Rebuild {@code node}'s subtree bottom-up, applying {@code f} to every node after
     * its children have been rebuilt; {@code f} returns its argument to keep a node.
     * Ranges are kept as they are. The variant coverage is {@link #rerangeVal}'s.
     */
    static AstNode<StmtExpr> map(AstNode<StmtExpr> node,
                                 java.util.function.UnaryOperator<AstNode<StmtExpr>> f) {
        return f.apply(new AstNode<>(mapVal(node.val(), f), node.source()));
    }

    private static Optional<AstNode<StmtExpr>> map(Optional<AstNode<StmtExpr>> node,
                                                   java.util.function.UnaryOperator<AstNode<StmtExpr>> f) {
        return node.map(n -> map(n, f));
    }

    private static List<AstNode<StmtExpr>> mapAll(List<AstNode<StmtExpr>> nodes,
                                                  java.util.function.UnaryOperator<AstNode<StmtExpr>> f) {
        var out = new ArrayList<AstNode<StmtExpr>>(nodes.size());
        for (var n : nodes) {
            out.add(map(n, f));
        }
        return out;
    }

    private static AstNode<Variable> mapVar(AstNode<Variable> v,
                                            java.util.function.UnaryOperator<AstNode<StmtExpr>> f) {
        return v.val() instanceof Variable.Field fld
                ? new AstNode<>(new Variable.Field(map(fld.target(), f), fld.fieldName()), v.source())
                : v;
    }

    private static StmtExpr mapVal(StmtExpr e, java.util.function.UnaryOperator<AstNode<StmtExpr>> f) {
        return switch (e) {
            case StmtExpr.IfThenElse n -> new StmtExpr.IfThenElse(map(n.cond(), f),
                    map(n.thenBranch(), f), map(n.elseBranch(), f));
            case StmtExpr.Block n -> new StmtExpr.Block(mapAll(n.statements(), f), n.label(),
                    mapAll(n.invariants(), f), map(n.decreases(), f));
            case StmtExpr.While n -> new StmtExpr.While(map(n.cond(), f), mapAll(n.invariants(), f),
                    map(n.decreases(), f), map(n.body(), f), n.postTest());
            case StmtExpr.Return n -> new StmtExpr.Return(mapAll(n.values(), f));
            case StmtExpr.Var n -> n.var_() instanceof Variable.Field fld
                    ? new StmtExpr.Var(new Variable.Field(map(fld.target(), f), fld.fieldName()))
                    : n;
            case StmtExpr.Assign n -> new StmtExpr.Assign(
                    n.targets().stream().map(t -> mapVar(t, f)).toList(), map(n.value(), f));
            case StmtExpr.IncrDecr n -> new StmtExpr.IncrDecr(n.mode(), n.op(), mapVar(n.target(), f));
            case StmtExpr.CompoundAssign n -> new StmtExpr.CompoundAssign(n.op(),
                    mapVar(n.target(), f), map(n.rhs(), f));
            case StmtExpr.PureFieldUpdate n -> new StmtExpr.PureFieldUpdate(map(n.target(), f),
                    n.fieldName(), map(n.newValue(), f));
            case StmtExpr.Call n -> new StmtExpr.Call(map(n.receiver(), f), n.callee(), mapAll(n.arguments(), f),
                    n.typeArgs());
            case StmtExpr.ReferenceEquals n -> new StmtExpr.ReferenceEquals(map(n.lhs(), f), map(n.rhs(), f));
            case StmtExpr.AsType n -> new StmtExpr.AsType(map(n.target(), f), n.targetType());
            case StmtExpr.IsType n -> new StmtExpr.IsType(map(n.target(), f), n.type());
            case StmtExpr.Quantifier n -> new StmtExpr.Quantifier(n.mode(), n.param(),
                    map(n.trigger(), f), map(n.body(), f));
            case StmtExpr.Assigned n -> new StmtExpr.Assigned(map(n.name(), f));
            case StmtExpr.Old n -> new StmtExpr.Old(map(n.value(), f), n.label());
            case StmtExpr.Fresh n -> new StmtExpr.Fresh(map(n.value(), f));
            case StmtExpr.Assert n -> new StmtExpr.Assert(map(n.condition(), f), n.summary());
            case StmtExpr.Assume n -> new StmtExpr.Assume(map(n.condition(), f));
            case StmtExpr.Throw n -> new StmtExpr.Throw(map(n.value(), f));
            case StmtExpr.Try n -> {
                var catches = new ArrayList<CatchClause>(n.catches().size());
                for (var c : n.catches()) {
                    catches.add(new CatchClause(c.binding(), map(c.predicate(), f),
                            map(c.body(), f), c.bindingType()));
                }
                yield new StmtExpr.Try(map(n.body(), f), catches, map(n.finally_(), f));
            }
            case StmtExpr.ProveBy n -> new StmtExpr.ProveBy(map(n.value(), f), map(n.proof(), f));
            case StmtExpr.ContractOf n -> new StmtExpr.ContractOf(n.type(), map(n.function(), f));
            case StmtExpr.Goto n -> n;
            case StmtExpr.LiteralInt n -> n;
            case StmtExpr.LiteralBool n -> n;
            case StmtExpr.LiteralString n -> n;
            case StmtExpr.LiteralDecimal n -> n;
            case StmtExpr.LiteralBv n -> n;
            case StmtExpr.New n -> n;
            case StmtExpr.This n -> n;
            case StmtExpr.Abstract n -> n;
            case StmtExpr.All n -> n;
            case StmtExpr.Hole n -> n;
            case StmtExpr.OldGuarantee n -> new StmtExpr.OldGuarantee(map(n.value(), f));
            case StmtExpr.OldRelies n -> new StmtExpr.OldRelies(map(n.value(), f));
            case StmtExpr.Yield n -> n;
            case StmtExpr.Resume n -> new StmtExpr.Resume(map(n.target(), f), map(n.value(), f));
            case StmtExpr.HasNext n -> new StmtExpr.HasNext(map(n.target(), f));
            case StmtExpr.Snapshot n -> n;
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

    // --- Negation over $op static Calls ---

    /** Logical negation of a condition: a {@code $not} unwraps, anything else wraps in {@code $not}. */
    static AstNode<StmtExpr> negate(AstNode<StmtExpr> e) {
        if (e.val() instanceof StmtExpr.Call call && call.receiver().isEmpty() && call.callee().text().equals("$not")
                && call.arguments().size() == 1) {
            return call.arguments().get(0);
        }
        return new AstNode<>(LaurelFactory.staticCall(LaurelFactory.reservedIdent("$not"), List.of(e), List.of()), e.source());
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
            case StmtExpr.Call c when c.receiver().isEmpty() -> booleanProc(c.callee().text());
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

    /** The {@code $op} static Call's proc name when {@code e} is one, else null. */
    static String staticCallName(StmtExpr e) {
        return e instanceof StmtExpr.Call c && c.receiver().isEmpty() ? c.callee().text() : null;
    }
}
