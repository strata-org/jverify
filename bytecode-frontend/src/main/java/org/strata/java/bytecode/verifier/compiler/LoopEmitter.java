package org.strata.java.bytecode.verifier.compiler;

import org.strata.java.bytecode.frontend.DominanceFinder;
import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.CatchClause;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.Variable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a loop's leading contract markers into the invariants of the labelled
 * block that starts at the loop header ({@link BlockEmitter}), which Laurel's
 * {@code RestructureGotos} makes the loop. The markers follow the header's own
 * statements, which stay in the block: the condition's supporting assignments and
 * tests ({@link DominanceFinder#loopCond}), jump-only guards (the loop test, or a
 * user's {@code if (c) break;}), and counter snapshots ({@link HeaderScan}). A
 * marker behind anything else -- a user statement, or a later block -- is not a
 * loop contract: it stays put and refuses the method as a misplaced contract call.
 * Invariants convert (at their argument range, contract-checked),
 * reads/modifies/decreases drop, and pre/postconditions refuse.
 *
 * <p>Input with no CRT (compiled without {@code -Xjcov}) has no recorded condition
 * positions, so only jump-only guards are part of the header there. That fails
 * closed: a marker behind a header-embedded store or an implicit-throw guard is
 * refused rather than promoted.
 */
final class LoopEmitter {

    private final DominanceFinder dom;
    private final BodyTranslator body;
    private final TranslationContext ctx;

    private final BlockEmitter emitter;

    LoopEmitter(BlockEmitter s) {
        this.emitter = s;
        this.dom = s.dom;
        this.body = s.body;
        this.ctx = s.ctx;
    }

    /**
     * {@code stmts} with every loop header block carrying its invariants. The header
     * statements may span several labelled blocks: a {@code break} guard jumps over
     * its {@code goto} to the block holding the rest of the loop, which the scan then
     * continues in when the guard's block is all header and the next block is entered
     * from the header blocks only.
     */
    List<AstNode<StmtExpr>> attachInvariants(List<AstNode<StmtExpr>> stmts, Map<String, Integer> headers) {
        if (headers.isEmpty()) {
            return stmts;
        }
        Map<String, List<AstNode<StmtExpr>>> blocks = new HashMap<>();
        for (AstNode<StmtExpr> st : stmts) {
            collect(st, blocks);
        }
        Map<String, List<AstNode<StmtExpr>>> invariants = new HashMap<>();
        for (var e : headers.entrySet()) {
            if (!blocks.containsKey(e.getKey())) {
                continue;
            }
            List<AstNode<StmtExpr>> found = new ArrayList<>();
            HeaderScan scan = new HeaderScan();
            List<String> chain = new ArrayList<>();
            String cur = e.getKey();
            while (cur != null && !chain.contains(cur) && blocks.containsKey(cur)) {
                chain.add(cur);
                String next = scanPastHeader(blocks.get(cur), found, e.getValue(), scan, cur);
                cur = next != null && emitter.enteredOnlyFrom(next, chain) ? next : null;
            }
            invariants.put(e.getKey(), found);
        }
        List<AstNode<StmtExpr>> out = new ArrayList<>();
        for (AstNode<StmtExpr> st : stmts) {
            out.add(rebuild(st, blocks, invariants));
        }
        return out;
    }

    private static void collect(AstNode<StmtExpr> st, Map<String, List<AstNode<StmtExpr>>> blocks) {
        switch (st.val()) {
            case StmtExpr.Block b -> {
                b.label().ifPresent(l -> blocks.put(l, new ArrayList<>(b.statements())));
                b.statements().forEach(x -> collect(x, blocks));
            }
            case StmtExpr.Try t -> {
                collect(t.body(), blocks);
                t.catches().forEach(c -> collect(c.body(), blocks));
            }
            default -> {
            }
        }
    }

    private static AstNode<StmtExpr> rebuild(AstNode<StmtExpr> st, Map<String, List<AstNode<StmtExpr>>> blocks,
                                             Map<String, List<AstNode<StmtExpr>>> invariants) {
        switch (st.val()) {
            case StmtExpr.Block b -> {
                List<AstNode<StmtExpr>> inner = new ArrayList<>();
                for (AstNode<StmtExpr> x : b.label().map(blocks::get).orElse(b.statements())) {
                    inner.add(rebuild(x, blocks, invariants));
                }
                List<AstNode<StmtExpr>> invs = new ArrayList<>(b.invariants());
                b.label().map(invariants::get).ifPresent(invs::addAll);
                return new AstNode<>(new StmtExpr.Block(inner, b.label(), invs, b.decreases()), st.source());
            }
            case StmtExpr.Try t -> {
                List<CatchClause> catches = new ArrayList<>();
                for (CatchClause c : t.catches()) {
                    catches.add(new CatchClause(c.binding(), c.predicate(), rebuild(c.body(), blocks, invariants),
                            c.bindingType()));
                }
                return new AstNode<>(new StmtExpr.Try(rebuild(t.body(), blocks, invariants), catches, t.finally_()),
                        st.source());
            }
            default -> {
                return st;
            }
        }
    }

    /**
     * State of one loop's header scan: the counter snapshots accepted so far
     * and the locals the skipped header statements assign.
     *
     * <p>A lambda in a contract clause cannot capture the loop counter --
     * javac requires captured locals to be effectively final -- so the
     * supported idiom copies the counter into a final local first:
     *
     * <pre>
     *     while (i &lt; a.length) {
     *         final int i0 = i;
     *         invariant(forall((int j) -&gt; implies(0 &lt;= j &amp;&amp; j &lt; i0, ...)));
     * </pre>
     *
     * The copy executes in the loop BODY, but Laurel checks invariants at the
     * loop head, where {@code i0} is still the hoisted, unassigned variable.
     * Keeping the copy as an assignment would therefore check an arbitrary
     * value on entry and a stale one on back edges. Instead the copy is a
     * ghost DEFINITION: {@code renames} substitutes {@code i0 -> i} inside
     * every extracted invariant, which is exact because the copy runs before
     * anything else in the body -- unless a skipped header statement already
     * wrote the source variable (a store embedded in the loop condition,
     * e.g. {@code while (i++ < n)}): {@code written} tracks those, and a
     * copy from a written variable is not accepted (the markers behind it
     * then refuse as misplaced). A quantifier binder cannot shadow a
     * snapshot name -- Java forbids a lambda parameter shadowing a local in
     * scope -- so the substitution needs no scope tracking.
     */
    private static final class HeaderScan {
        /** Snapshot name -> the emitted name of the variable it copies. */
        final Map<String, String> renames = new HashMap<>();
        /** Locals assigned by the skipped header statements seen so far. */
        final Set<String> written = new HashSet<>();
    }

    /**
     * Scan {@code stmts} (mutated in place) past the header statements of the loop
     * at {@code header} and consume the marker run that follows them. The label of
     * the block the header continues in when all of {@code stmts} is header and its
     * jumps leave for exactly one block inside the loop, else null.
     */
    private String scanPastHeader(List<AstNode<StmtExpr>> stmts, List<AstNode<StmtExpr>> invariants,
                                  int header, HeaderScan scan, String label) {
        Set<String> inLoop = new java.util.LinkedHashSet<>();
        boolean jumps = false;
        for (int i = 0; i < stmts.size(); i++) {
            AstNode<StmtExpr> st = stmts.get(i);
            if (body.specStmts.get(st) != null || markerAfterPushes(stmts, i)) {
                if (inLoop.isEmpty()) {
                    consumeLeadingMarkers(stmts.subList(i, stmts.size()), invariants, scan);
                }
                return null;
            }
            if (body.isContractStep(st)) {
                continue;
            }
            Set<String> targets = headerJumpTargets(st, header);
            if (targets == null && !isConditionStmt(st, header)) {
                if (inLoop.isEmpty() && recordSnapshot(st, i > 0 ? stmts.get(i - 1) : null, scan)) {
                    continue;
                }
                return null;
            }
            if (targets != null) {
                inLoop.addAll(targets);
                jumps |= st.val() instanceof StmtExpr.Goto;
            }
            collectWrites(st.val() instanceof StmtExpr.IfThenElse cond ? cond.cond() : st, scan.written);
            if (writesInvalidateSnapshots(scan)) {
                return null;
            }
        }
        if (!jumps) {
            String fall = emitter.fallsInto(label);
            if (fall != null) {
                inLoop.add(fall);
            }
        }
        return inLoop.size() == 1 ? inLoop.iterator().next() : null;
    }

    /** {@code st} lowers the loop statement itself: it was emitted from one of the loop's own positions. */
    private boolean isConditionStmt(AstNode<StmtExpr> st, int header) {
        java.util.BitSet cond = dom.loopCond.get(header);
        Integer sq = body.stmtSeq.get(st);
        return cond != null && sq != null && body.seqToBci != null && sq >= 0 && sq < body.seqToBci.length
                && cond.get(body.seqToBci[sq]);
    }

    /**
     * The in-loop targets of a statement that is nothing but jumps -- an {@code if}
     * whose arms each hold one, or a bare one -- or null for any other statement. A
     * jump back to the header or out of the loop is the loop test or a
     * {@code continue}/{@code break}; one to a block inside the loop is where the
     * header continues, or skips user code.
     */
    private Set<String> headerJumpTargets(AstNode<StmtExpr> st, int header) {
        Set<String> out = new HashSet<>();
        if (st.val() instanceof StmtExpr.IfThenElse ite) {
            return armJump(ite.thenBranch(), header, out)
                    && (ite.elseBranch().isEmpty() || armJump(ite.elseBranch().get(), header, out)) ? out : null;
        }
        return st.val() instanceof StmtExpr.Goto && armJump(st, header, out) ? out : null;
    }

    private boolean armJump(AstNode<StmtExpr> arm, int header, Set<String> inLoop) {
        if (arm.val() instanceof StmtExpr.Block b) {
            return b.label().isEmpty() && b.statements().size() == 1 && armJump(b.statements().get(0), header, inLoop);
        }
        if (!(arm.val() instanceof StmtExpr.Goto g)) {
            return false;
        }
        Integer to = emitter.blockOfLabel(g.target());
        if (to == null) {
            return false;
        }
        if (to != header && dom.loops.get(header).contains(to)) {
            inLoop.add(g.target());
        }
        return true;
    }

    /**
     * A leading counter snapshot: a single-target assignment of one local
     * variable to another ({@code final int i0 = i;}). Recorded as a ghost
     * definition in {@code scan.renames} (chained through earlier snapshots,
     * so {@code final int a = i; final int b = a;} maps both to {@code i}).
     * Not accepted -- so the scan ends and any markers behind it refuse as
     * misplaced -- when the source variable was assigned by a skipped header
     * statement (its loop-head value differs from its value here), or when
     * either name is synthetic: a hoist-renamed {@code name@N} (the lambda's
     * capture refers to the plain source name, which would no longer match)
     * or a {@code $}/{@code t$} translator temp.
     */
    private static boolean recordSnapshot(AstNode<StmtExpr> st, AstNode<StmtExpr> prev, HeaderScan scan) {
        if (!(st.val() instanceof StmtExpr.Assign a) || a.targets().size() != 1) {
            return false;
        }
        String lhs = switch (a.targets().get(0).val()) {
            case Variable.Local l -> l.name().text();
            case Variable.Declare d -> d.parameter().name().text();
            case Variable.Field f -> null;
            case Variable.Stack k -> null;
        };
        if (lhs == null || lhs.indexOf('@') >= 0 || lhs.indexOf('$') >= 0) {
            return false;
        }
        AstNode<StmtExpr> value = ArrayAccessTranslator.stripLoadFacts(a.value());
        if (BodyTranslator.isStackRead(value) && prev != null && prev.val() instanceof StmtExpr.Assign push
                && push.targets().size() == 1 && push.targets().get(0).val() instanceof Variable.Stack) {
            value = ArrayAccessTranslator.stripLoadFacts(push.value());
        }
        if (!(value.val() instanceof StmtExpr.Var v && v.var_() instanceof Variable.Local src)) {
            return false;
        }
        String rhs = src.name().text();
        if (rhs.indexOf('@') >= 0 || rhs.indexOf('$') >= 0) {
            return false;
        }
        rhs = scan.renames.getOrDefault(rhs, rhs);
        if (scan.written.contains(rhs)) {
            return false;
        }
        scan.renames.put(lhs, rhs);
        return true;
    }

    /**
     * A skipped header statement assigned a variable that a recorded snapshot
     * names (its ghost name or the source it copies): the definition is stale
     * for the markers that follow.
     */
    private static boolean writesInvalidateSnapshots(HeaderScan scan) {
        for (var e : scan.renames.entrySet()) {
            if (scan.written.contains(e.getKey()) || scan.written.contains(e.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** Add every local the subtree assigns (assignment, compound, incr/decr) to {@code out}. */
    private static void collectWrites(AstNode<StmtExpr> n, Set<String> out) {
        switch (n.val()) {
            case StmtExpr.Assign a -> {
                for (var t : a.targets()) {
                    addLocalName(t, out);
                }
            }
            case StmtExpr.CompoundAssign c -> addLocalName(c.target(), out);
            case StmtExpr.IncrDecr d -> addLocalName(d.target(), out);
            default -> {
            }
        }
        for (var c : BodyTranslator.childrenOf(n.val())) {
            collectWrites(c, out);
        }
    }

    private static void addLocalName(AstNode<Variable> target, Set<String> out) {
        switch (target.val()) {
            case Variable.Local l -> out.add(l.name().text());
            case Variable.Declare d -> out.add(d.parameter().name().text());
            case Variable.Field f -> {
            }
            case Variable.Stack k -> {
            }
        }
    }

    /** Every free use of a snapshot name replaced by the variable it copies. */
    private static AstNode<StmtExpr> applySnapshots(AstNode<StmtExpr> arg, Map<String, String> renames) {
        if (renames.isEmpty()) {
            return arg;
        }
        return LaurelTrees.map(arg, n -> {
            if (n.val() instanceof StmtExpr.Var v && v.var_() instanceof Variable.Local l) {
                String to = renames.get(l.name().text());
                if (to != null) {
                    return new AstNode<>(new StmtExpr.Var(
                            new Variable.Local(LaurelFactory.reservedIdent(to))), n.source());
                }
            }
            return n;
        });
    }

    private static boolean sameModuloLabels(AstNode<StmtExpr> a, AstNode<StmtExpr> b) {
        java.util.function.Function<AstNode<StmtExpr>, String> canon = n ->
                n.toString().replaceAll("(skip|join|else|bb)_?\\d+", "L");
        return canon.apply(a).equals(canon.apply(b));
    }

    private boolean markerAfterPushes(List<AstNode<StmtExpr>> stmts, int from) {
        int j = from;
        while (j < stmts.size() && body.isContractStep(stmts.get(j))) {
            j++;
        }
        return j > from && j < stmts.size() && body.specStmts.containsKey(stmts.get(j));
    }

    private int consumeLeadingMarkers(List<AstNode<StmtExpr>> stmts, List<AstNode<StmtExpr>> invariants,
                                      HeaderScan scan) {
        int consumed = 0;
        var queue = new java.util.ArrayList<AstNode<StmtExpr>>();
        for (int i = 0; i < stmts.size(); i++) {
            var spec = body.specStmts.get(stmts.get(i));
            if (spec == null && body.isContractStep(stmts.get(i))) {
                queue.add(stmts.get(i));
                continue;
            }
            if (spec == null) {
                break;
            }
            var prefix = body.contractPrefix(queue);
            queue.clear();
            consumed = i + 1;
            // Consumed as a loop-contract clause: removed from the method
            // spec markers (the driver's refusal passes must not see it).
            body.specStmts.remove(stmts.get(i));
            switch (spec.name()) {
                case "precondition", "postcondition" ->
                        throw new JavaViolationException("preconditions are not allowed in a loop");
                case "invariant" -> {
                    if (spec.args().size() != 1) {
                        throw new JavaViolationException(
                                "A call to 'invariant' may have only one argument");
                    }
                    FileRange argRange = stmts.get(i).source();
                    AstNode<StmtExpr> arg;
                    if (spec.lambdaArg() != null) {
                        arg = body.translateLambdaBody(spec.lambdaArg(), Map.of());
                    } else {
                        arg = spec.args().get(0);
                        body.checkContractArg(arg);
                    }
                    arg = ArrayAccessTranslator.pureLoads(BodyTranslator.withPrefix(prefix,
                            body.coerce(ArrayAccessTranslator.pureLoads(arg, ctx.lf), "Z")), ctx.lf);
                    arg = applySnapshots(arg, scan.renames);
                    body.checkContractCode(arg);
                    AstNode<StmtExpr> inv = LaurelTrees.rerange(arg, argRange, ctx.method.statementNodes);
                    // A short-circuit || duplicates the body's marker run
                    // into each header test's arm: drop the extra copies.
                    if (invariants.stream().noneMatch(x -> sameModuloLabels(x, inv))) {
                        // Pinned: the loop's own placement must not restamp
                        // the invariant's argument-range subtree.
                        ctx.method.statementNodes.add(inv);
                        invariants.add(inv);
                    }
                }
                case "decreases", "reads", "modifies", "throwsOn" -> {
                    // recognized so the header scan continues; dropped at
                    // Laurel emission (no Laurel counterpart)
                }
                default -> {
                }
            }
        }
        if (consumed > 0) {
            stmts.subList(0, consumed).clear();
        }
        return consumed;
    }
}
