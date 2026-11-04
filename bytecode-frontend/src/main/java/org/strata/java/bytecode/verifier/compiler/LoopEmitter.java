package org.strata.java.bytecode.verifier.compiler;

import org.strata.java.bytecode.frontend.BasicBlock;
import org.strata.java.bytecode.frontend.ControlFlowGraph;
import org.strata.java.bytecode.frontend.DominanceFinder;
import org.strata.java.bytecode.frontend.StructException;
import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.StmtExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Emits a CFG loop region as a labelled Laurel block that the loop's back
 * edges jump to the start of: {@code { header; body } L invariant I}, with
 * every edge back to the header a {@code goto L} and every edge to the loop's
 * exit an {@code exit L}. No loop shape (do-while, pre-tested, for) is
 * recovered: the header's test is just the first {@code if} in the block, and
 * Laurel's {@code RestructureGotos} pass turns the block into a loop. Leading
 * loop-contract markers become the block's invariants.
 */
final class LoopEmitter {

    private final ControlFlowStructurer s;
    private final ControlFlowGraph cfg;
    private final DominanceFinder dom;
    private final BodyTranslator body;
    private final TranslationContext ctx;

    LoopEmitter(ControlFlowStructurer s) {
        this.s = s;
        this.cfg = s.cfg;
        this.dom = s.dom;
        this.body = s.body;
        this.ctx = s.ctx;
    }

    int emitLoop(int header, Set<Integer> stop, List<AstNode<StmtExpr>> out) {
        if (!s.emittingLoops.add(header)) {
            throw new StructException("loop re-entry B" + header);
        }
        try {
            return emitLoop0(header, stop, out);
        } finally {
            s.emittingLoops.remove(header);
        }
    }

    int emitLoop0(int header, Set<Integer> stop, List<AstNode<StmtExpr>> out) {
        Set<Integer> bodyBlocks = dom.loops.get(header);
        int exit = loopExit(header, bodyBlocks);
        ControlFlowStructurer.Ctx c = new ControlFlowStructurer.Ctx(true, header, exit, ctx.freshLabel("loop"));
        s.ctxStack.push(c);
        s.loopEntryPass.add(header);
        s.activeCondPcs.push(dom.loopCond.getOrDefault(header, EMPTY_COND));
        List<AstNode<StmtExpr>> bodyStmts;
        try {
            bodyStmts = new ArrayList<>(s.region(header, ControlFlowStructurer.plus(ControlFlowStructurer.plus(stop, header), exit)));
        } finally {
            s.activeCondPcs.pop();
            s.loopEntryPass.remove(header);
            s.ctxStack.pop();
        }
        List<AstNode<StmtExpr>> invariants = extractLoopInvariants(bodyStmts, c.label);
        out.add(s.place(body.e(new StmtExpr.Block(bodyStmts, Optional.of(c.label), invariants, Optional.empty())),
                header));
        return exit;
    }

    /**
     * Where control continues after the loop: the header's out-of-loop
     * successor for a test at the top, else the latch's for a test at the
     * bottom, else the first edge leaving the body.
     */
    private int loopExit(int header, Set<Integer> bodyBlocks) {
        int fromHeader = soleExit(cfg.blocks.get(header), bodyBlocks);
        if (fromHeader >= 0) {
            return fromHeader;
        }
        Integer latch = dom.loopTail.get(header);
        if (latch != null) {
            int fromLatch = soleExit(cfg.blocks.get(latch), bodyBlocks);
            if (fromLatch >= 0) {
                return fromLatch;
            }
        }
        return s.firstExit(bodyBlocks);
    }

    /** Pushed for a loop with no recorded own-position set (no CRT): nothing is condition-emitted. */
    private static final java.util.BitSet EMPTY_COND = new java.util.BitSet();

    private static int soleExit(BasicBlock b, Set<Integer> bodyBlocks) {
        if (!b.isConditional() || bodyBlocks.contains(b.fall) == bodyBlocks.contains(b.branch)) {
            return -1;
        }
        return bodyBlocks.contains(b.fall) ? b.branch : b.fall;
    }

    /**
     * The loop's contract markers, removed from where they were emitted.
     * The markers follow the loop-header statements, which all stay inside
     * the block: the condition's supporting assignments (e.g. a store written
     * in the condition itself, or an array-read temp), the condition's tests
     * (whose in-loop arm leaves or restarts the loop, or -- short-circuited
     * -- nests the rest of the header), and implicit-throw guards (e.g. the
     * divide-by-zero check a {@code /} in the condition compiles to). The
     * scan skips exactly the statements the condition emission produced
     * ({@link ControlFlowStructurer#condStmts}, recorded while this loop's
     * {@link #conditionBlocks} were translated), plus a user-written leading
     * jump-only guard (e.g. {@code if (c) break;} at the top of a
     * {@code while (true)}, which the old pre-tested-while recovery made the
     * loop condition), descends into those tests' arms, and consumes the
     * marker runs it finds (several, deduplicated, when short-circuiting
     * duplicated them). A marker behind anything else -- a user statement,
     * or a user {@code if}'s arm -- is NOT a loop contract: it stays put and
     * refuses the method as a misplaced contract call. Invariants convert
     * (at their argument range, contract-checked), reads/modifies/decreases
     * drop, and pre/postconditions refuse.
     */
    private List<AstNode<StmtExpr>> extractLoopInvariants(List<AstNode<StmtExpr>> bodyStmts, String label) {
        var invariants = new ArrayList<AstNode<StmtExpr>>();
        scanPastHeader(bodyStmts, invariants, label);
        return invariants;
    }

    /**
     * Scan {@code stmts} (mutated in place) past leading condition-emitted
     * statements and jump-only guards for the loop's contract markers; true
     * when a marker run was consumed. Every condition-test arm is scanned,
     * not just the first with markers: a short-circuit {@code ||} duplicates
     * the loop body -- markers included -- into the arm of each test, and a
     * copy left behind would refuse the method as a misplaced contract call
     * ({@code consumeLeadingMarkers} drops the duplicate invariants
     * themselves).
     *
     * <p>Input with no CRT (compiled without {@code -Xjcov} -- the DEBUG and
     * BARE tiers) has no recorded statement boundaries to attribute the
     * condition with ({@code DominanceFinder.loopCond} is empty), so nothing
     * is condition-emitted; only jump-only guards and the trailing header
     * test -- both identified by shape, not position evidence -- are part of
     * the header there. That FAILS CLOSED: a marker behind a header-embedded
     * store, an implicit-throw guard, or a short-circuit test is refused as
     * a misplaced contract call on those tiers rather than promoted --
     * promoting on structure alone also promoted markers behind user
     * assignments and inside user {@code if}s, which can verify a false
     * invariant.
     */
    private boolean scanPastHeader(List<AstNode<StmtExpr>> stmts, List<AstNode<StmtExpr>> invariants,
                                   String label) {
        boolean found = false;
        for (int i = 0; i < stmts.size(); i++) {
            AstNode<StmtExpr> st = stmts.get(i);
            if (body.specStmts.get(st) != null) {
                return consumeLeadingMarkers(stmts.subList(i, stmts.size()), invariants) > 0 || found;
            }
            boolean jumpGuard = isJumpGuard(st, label);
            boolean headerTest = !jumpGuard && i == stmts.size() - 1 && isHeaderTest(st, label);
            if (!jumpGuard && !headerTest && !s.condStmts.contains(st)) {
                return found;   // neither condition-emitted nor a header-shaped test: past the header
            }
            if (!jumpGuard && st.val() instanceof StmtExpr.IfThenElse ite) {
                AstNode<StmtExpr> thenB = scanArm(ite.thenBranch(), invariants, label);
                AstNode<StmtExpr> elseB = ite.elseBranch().isPresent()
                        ? scanArm(ite.elseBranch().get(), invariants, label) : null;
                if (thenB != null || elseB != null) {
                    stmts.set(i, new AstNode<>(new StmtExpr.IfThenElse(ite.cond(),
                            thenB != null ? thenB : ite.thenBranch(),
                            elseB != null ? Optional.of(elseB) : ite.elseBranch()), st.source()));
                    found = true;
                }
                // The in-loop path falls through a marker-less guard (e.g. an
                // implicit throw), and a sibling test may hold a duplicate
                // marker run: the header continues here either way.
            }
            // Other condition-emitted statements (condition-embedded stores,
            // implicit-throw guards) and jump-only guards: skip.
        }
        return found;
    }

    /**
     * A leading {@code if} whose arms hold nothing but a jump out of, or back
     * to, THIS loop (its {@code break}/{@code continue} target the block
     * labelled {@code label}): part of the loop header, like the loop
     * condition it would have been under the old pre-tested-while recovery.
     * A guard jumping an ENCLOSING loop, or an arm with any other content,
     * is body.
     */
    private static boolean isJumpGuard(AstNode<StmtExpr> st, String label) {
        return st.val() instanceof StmtExpr.IfThenElse ite
                && isJumpOnly(ite.thenBranch(), label)
                && (ite.elseBranch().isEmpty() || isJumpOnly(ite.elseBranch().get(), label));
    }

    /** The arm's only content is a {@code goto} (either direction) to {@code label}. */
    private static boolean isJumpOnly(AstNode<StmtExpr> arm, String label) {
        if (arm.val() instanceof StmtExpr.Block b) {
            return b.label().isEmpty() && b.statements().size() == 1
                    && isJumpOnly(b.statements().get(0), label);
        }
        return arm.val() instanceof StmtExpr.Goto g && g.target().equals(label);
    }

    /**
     * The block's trailing header test: a one-armed {@code if} whose arm
     * re-enters the loop (ends in the back edge) while its fall-through
     * leaves the block -- the loop's own pre-test. A leading
     * {@code if (c) break;} in a {@code while (true)}, and a plain
     * {@code while (cond)}, both lower to this: the structurer negates the
     * exit test and nests the body (markers first) in its arm. The shape
     * identifies the test even without CRT evidence; the three structural
     * requirements (last statement, no else, arm ending in the back edge)
     * are what a user {@code if} inside the body cannot all meet -- anything
     * following it, an else arm, or an arm that does not re-enter the loop
     * disqualifies it, and its markers then refuse as misplaced.
     */
    private static boolean isHeaderTest(AstNode<StmtExpr> st, String label) {
        return st.val() instanceof StmtExpr.IfThenElse ite
                && ite.elseBranch().isEmpty()
                && ite.thenBranch().val() instanceof StmtExpr.Block b
                && !b.statements().isEmpty()
                && b.statements().get(b.statements().size() - 1).val() instanceof StmtExpr.Goto g
                && g.target().equals(label)
                && g.destination() instanceof org.strata.jverify.laurel.GotoDestination.BlockStart;
    }

    /** The arm with its marker run consumed, or null when it has none. */
    private AstNode<StmtExpr> scanArm(AstNode<StmtExpr> branch, List<AstNode<StmtExpr>> invariants,
                                      String label) {
        if (!(branch.val() instanceof StmtExpr.Block blk)) {
            return null;
        }
        List<AstNode<StmtExpr>> stmts = new ArrayList<>(blk.statements());
        if (!scanPastHeader(stmts, invariants, label)) {
            return null;
        }
        return new AstNode<>(new StmtExpr.Block(stmts, blk.label(), blk.invariants(), blk.decreases()),
                branch.source());
    }

    private int consumeLeadingMarkers(List<AstNode<StmtExpr>> stmts, List<AstNode<StmtExpr>> invariants) {
        int i = 0;
        for (; i < stmts.size(); i++) {
            var spec = body.specStmts.get(stmts.get(i));
            if (spec == null) {
                break;
            }
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
                    arg = body.coerce(ArrayAccessTranslator.stripLoadFacts(arg), "Z");
                    AstNode<StmtExpr> inv = LaurelTrees.rerange(arg, argRange, ctx.method.statementNodes);
                    // A short-circuit || duplicates the body's marker run
                    // into each header test's arm: drop the extra copies.
                    if (!invariants.contains(inv)) {
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
        if (i > 0) {
            stmts.subList(0, i).clear();
        }
        return i;
    }
}
