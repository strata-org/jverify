package org.strata.java.bytecode.verifier.compiler;

import org.strata.java.bytecode.frontend.BasicBlock;
import org.strata.java.bytecode.frontend.ControlFlowGraph;
import org.strata.java.bytecode.frontend.DominanceFinder;
import org.strata.java.bytecode.frontend.Trap;
import org.strata.java.bytecode.frontend.Span;
import org.strata.java.bytecode.frontend.StructException;
import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.CatchClause;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.GotoDestination;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.Operation;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.Variable;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The control-flow structurer: the region walker over the CFG, emitting
 * Laurel {@code AstNode<StmtExpr>} directly. Handles if/else, ternary and
 * boolean diamonds, and asserts itself; loops, switches, and
 * try/catch/finally/synchronized are delegated to {@link LoopEmitter},
 * {@link SwitchEmitter}, and {@link TryEmitter}, which share this walker's
 * region/placement services and visited-set bookkeeping.
 *
 * <p>Label scheme: each loop is a labelled block (see {@link LoopEmitter});
 * an edge back to its header is a {@code goto} to the block's start and an
 * edge to its exit an {@code exit} past the block's end, wherever the edge
 * occurs ({@link #jumpTo}).
 */
final class ControlFlowStructurer {

    final ControlFlowGraph cfg;
    final DominanceFinder dom;
    final BodyTranslator body;
    final TranslationContext ctx;
    final boolean[] visited;

    /** Enclosing breakable context (innermost first): loop or switch. */
    static final class Ctx {
        final boolean loop;
        final int header, exit;
        final String label;     // the loop's block label, or a switch's arm-break sentinel

        Ctx(boolean loop, int header, int exit, String label) {
            this.loop = loop;
            this.header = header;
            this.exit = exit;
            this.label = label;
        }
    }

    final Deque<Ctx> ctxStack = new ArrayDeque<>();
    final Set<Integer> emittingLoops = new HashSet<>();
    final Set<Integer> loopEntryPass = new HashSet<>();
    final Map<Integer, List<Trap>> triesByStart = new HashMap<>();
    final Set<Integer> emittedTries = new HashSet<>();
    final Set<Integer> emittedSync = new HashSet<>();
    final Map<Integer, Integer> skipLead = new HashMap<>();
    final Map<Integer, Integer> skipTail = new HashMap<>();
    /**
     * Per emitted loop (innermost on top): the loop's own bytecode positions
     * ({@code DominanceFinder.loopCond} -- condition tests and their support,
     * for-each bookkeeping; empty when absent). Pushed by {@link LoopEmitter}
     * around the loop's region walk.
     */
    final Deque<java.util.BitSet> activeCondPcs = new ArrayDeque<>();
    /**
     * Statements emitted from those positions: exactly the loop-statement
     * lowering (its tests, condition-embedded stores, implicit-throw guards,
     * a for-each's hidden binding). {@link LoopEmitter}'s contract-marker
     * scan skips only these, so a marker behind a user statement is refused
     * rather than promoted to the loop head.
     */
    final Set<AstNode<StmtExpr>> condStmts = Collections.newSetFromMap(new IdentityHashMap<>());
    int guard = 0;
    int depth = 0;

    /** Per-construct emitters around this region walker. */
    final LoopEmitter loops;
    final SwitchEmitter switches;
    final TryEmitter tries;

    ControlFlowStructurer(ControlFlowGraph cfg, DominanceFinder dom, BodyTranslator body, TranslationContext ctx) {
        this.cfg = cfg;
        this.dom = dom;
        this.body = body;
        this.ctx = ctx;
        this.visited = new boolean[cfg.blocks.size()];
        for (Trap e : cfg.excs) {
            triesByStart.computeIfAbsent(e.startBlk(), k -> new ArrayList<>()).add(e);
        }
        this.loops = new LoopEmitter(this);
        this.switches = new SwitchEmitter(this);
        this.tries = new TryEmitter(this);
        markPatternBindTests();
    }

    /**
     * Record every {@code INSTANCEOF} that tests a pattern-bind shape, so
     * {@link BodyTranslator#typeCheck} refuses an {@code instanceof} with a
     * pattern: Laurel's {@code is}
     * has nowhere to put the binding. {@link #foldChainPatternBind} then skips the
     * LOAD/CHECKCAST/STORE that would have introduced it, so without this the
     * binding is simply absent and the arm fails with
     * {@code Resolution failed: 'c' is not defined} (FlowTyping).
     *
     * <p>Done as a prescan rather than inside {@code foldChainPatternBind} because
     * the test instruction is translated before that fold runs, and the same shape
     * is reached from the value-diamond path too.
     *
     * <p>The instructions alone do NOT identify a pattern: a hand-written
     * {@code if (x instanceof C) { C c = (C) x; ... }} compiles to the identical
     * LOAD/CHECKCAST/STORE, and must translate. What differs is where the source
     * statements start. The manual cast is its own statement, so a CRT statement
     * range and a LineNumberTable entry both begin at its LOAD; a pattern's binding
     * lives inside the {@code if} condition, so neither does. Measured with
     * javac 25 -g -Xjcov. With neither table there is no evidence either way, and
     * the binding is treated as a pattern -- refusing is the side that cannot
     * mistranslate.
     */
    private void markPatternBindTests() {
        for (BasicBlock c : cfg.blocks) {
            if (!c.isConditional() || c.last.opcode() != Opcode.IFEQ) {
                continue;
            }
            int n = c.instrs.size();
            if (n < 2 || !(insAt(c, n - 2) instanceof TypeCheckInstruction ti)
                    || ti.opcode() != Opcode.INSTANCEOF) {
                continue;
            }
            int fb = c.fall;
            if (fb < 0 || fb >= cfg.blocks.size()) {
                continue;
            }
            BasicBlock b = cfg.blocks.get(fb);
            if (b.instrs.size() < 4
                    || !(insAt(b, 0) instanceof LoadInstruction)
                    || !(insAt(b, 1) instanceof TypeCheckInstruction cc)
                    || cc.opcode() != Opcode.CHECKCAST
                    || !(insAt(b, 2) instanceof StoreInstruction)) {
                continue;
            }
            if (startsOwnStatement(b.instrs.get(0))) {
                continue;
            }
            body.patternBindTests.add(ti);
        }
    }

    /** A source statement begins at the instruction at {@code seqIdx} (see markPatternBindTests). */
    private boolean startsOwnStatement(int seqIdx) {
        if (body.seqToBci == null || seqIdx < 0 || seqIdx >= body.seqToBci.length) {
            return false;
        }
        int pc = body.seqToBci[seqIdx];
        if (body.crt != null && !body.crt.isEmpty()) {
            return body.crt.stream().anyMatch(r -> r.startPc() == pc);
        }
        return body.lines != null && body.lines.startsAt(pc);
    }

    /** The structured method body as a Laurel block statement list. */
    List<AstNode<StmtExpr>> structure() {
        List<AstNode<StmtExpr>> root = new ArrayList<>(region(0, Set.of()));
        for (AstNode<StmtExpr> s : new ArrayList<>(body.stmts)) {
            root.add(s);
        }
        body.stmts.clear();
        // Safety: every reachable block must have been consumed.
        Deque<Integer> q = new ArrayDeque<>();
        q.add(0);
        boolean[] reach = new boolean[cfg.blocks.size()];
        reach[0] = true;
        while (!q.isEmpty()) {
            int x = q.poll();
            for (int s : cfg.blocks.get(x).succ) {
                if (!reach[s]) {
                    reach[s] = true;
                    q.add(s);
                }
            }
        }
        for (int i = 0; i < reach.length; i++) {
            if (reach[i] && !visited[i]) {
                throw new StructException("unstructured block B" + i);
            }
        }
        return root;
    }

    // --- Region walk ---

    List<AstNode<StmtExpr>> region(int entry, Set<Integer> stop) {
        if (++depth > 3000) {
            depth--;
            throw new StructException("recursion too deep");
        }
        try {
            return region0(entry, stop);
        } finally {
            depth--;
        }
    }

    List<AstNode<StmtExpr>> region0(int entry, Set<Integer> stop) {
        List<AstNode<StmtExpr>> out = new ArrayList<>();
        int cur = entry;
        while (cur >= 0 && (!stop.contains(cur) || loopEntryPass.remove(cur))) {
            if (++guard > 100000) {
                throw new StructException("structurer guard tripped");
            }
            BasicBlock b = cfg.blocks.get(cur);
            visited[cur] = true;

            boolean tryHere = triesByStart.containsKey(cur) && !emittedTries.contains(cur);
            boolean typedTryHere = tryHere && triesByStart.get(cur).stream().anyMatch(e -> e.type() != null);
            if (!typedTryHere && b.last != null && b.last.opcode() == Opcode.MONITORENTER
                    && !emittedSync.contains(cur)) {
                cur = tries.emitSync(cur, stop, out);
                continue;
            }
            if (tryHere) {
                cur = tries.emitTry(cur, stop, out);
                continue;
            }

            {
                Integer af = tryAssert(cur, out);
                if (af != null) {
                    cur = af;
                    continue;
                }
            }

            if (dom.isLoopHeader(cur) && !emittingLoops.contains(cur)) {
                cur = loops.emitLoop(cur, stop, out);
                continue;
            }

            if (switches.isStringSwitch(b)) {
                cur = switches.emitStringSwitch(cur, stop, out);
                continue;
            }
            Integer smerge = switches.switchValueDiamond(cur);
            if (smerge != null) {
                switches.emitSwitchExpr(cur, smerge, out);
                cur = smerge;
                continue;
            }
            if (b.isSwitch()) {
                cur = switches.emitSwitch(cur, stop, out);
                continue;
            }

            Integer merge = booleanDiamond(cur);
            if (merge != null) {
                emitDiamondValue(b, out);
                cur = merge;
                continue;
            }

            foldChainPatternBind(cur);

            Integer tmerge = valueDiamond(cur);
            if (tmerge != null) {
                emitValueDiamond(cur, tmerge, out);
                cur = tmerge;
                continue;
            }

            if (b.isConditional()) {
                cur = emitIf(cur, stop, out);
                continue;
            }

            int mark = out.size();
            translateBlock(b, out);
            markCondEmitted(cur, out, mark, false);
            boolean viaGoto = b.gotoTarget >= 0;
            int next = viaGoto ? b.gotoTarget : b.fall;
            AstNode<StmtExpr> je = loopEdge(b.id, next, viaGoto);
            if (je != null) {
                addLoopJump(out, je);
                return out;
            }
            cur = next;
        }
        // Running into an enclosing loop's header is its back edge, which
        // falling off the end of the loop's block would not take.
        if (cur >= 0 && !endsInJump(out)) {
            for (Ctx c : ctxStack) {
                if (c.loop && c.header == cur) {
                    addLoopJump(out, gotoStart(c.label));
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Append a loop jump -- but a back edge that would land right after a
     * trailing compound statement (if/else, a switch's {@code Block[bind,
     * cascade]}, try/catch/finally) goes INSIDE each falling-through tail
     * instead, so the compound stays the region's last statement. Laurel's
     * Resolution synthesizes a type for an if/else that is not in tail
     * position and rejects arms whose trailing assignments have different
     * types (a non-tail block -- including a lowered try -- synthesizes its
     * last statement the same way, so wrapping the compound would not
     * help); in tail position it only checks them.
     */
    void addLoopJump(List<AstNode<StmtExpr>> out, AstNode<StmtExpr> jump) {
        boolean backEdge = jump.val() instanceof StmtExpr.Goto g
                && g.destination() instanceof GotoDestination.BlockStart;
        if (!backEdge) {
            out.add(jump);
            return;
        }
        placeBackEdge(out, jump);
    }

    /**
     * Place the back edge {@code jump}: inside the falling-through tails of a
     * trailing compound statement, else appended. Fail-closed self-check: a
     * falling-through path that the distribution left WITHOUT the back edge
     * would silently exit the loop (a false invariant could then verify), so
     * any tail shape the distribution does not cover refuses the method
     * instead of emitting the loop. Package-private static for direct unit
     * testing.
     */
    static void placeBackEdge(List<AstNode<StmtExpr>> out, AstNode<StmtExpr> jump) {
        if (!distributeIntoTail(out, jump)) {
            out.add(jump);
            return;
        }
        requireBackEdgeOnEveryPath(out.get(out.size() - 1));
    }

    /**
     * The fail-closed gate: a distribution result must not still fall
     * through -- a falling path without the back edge would exit the loop
     * where the program iterates. Package-private so the refusal is pinned
     * at the unit level against the miss shape.
     */
    static void requireBackEdgeOnEveryPath(AstNode<StmtExpr> distributed) {
        if (fallsThroughStmt(distributed)) {
            throw new JavaViolationException(
                    "loop body shape is not supported: a path would fall out of the loop "
                            + "instead of taking its back edge");
        }
    }

    /**
     * Control can run off the end of {@code stmt} (conservative: any shape
     * not known to end every path in a jump, return, or throw counts as
     * falling through). The independent re-check behind
     * {@link #placeBackEdge}'s fail-closed gate: it must stay at least as
     * pessimistic as {@link #withJumpInTails}'s cannot-fall classification.
     */
    static boolean fallsThroughStmt(AstNode<StmtExpr> stmt) {
        StmtExpr v = stmt.val();
        if (v instanceof StmtExpr.Goto || v instanceof StmtExpr.Return || v instanceof StmtExpr.Throw) {
            return false;
        }
        if (v instanceof StmtExpr.Block blk) {
            if (blk.label().isPresent()) {
                return true;    // a loop: its exits land right after it
            }
            return blk.statements().isEmpty()
                    || fallsThroughStmt(blk.statements().get(blk.statements().size() - 1));
        }
        if (v instanceof StmtExpr.IfThenElse ite) {
            return ite.elseBranch().isEmpty()
                    || fallsThroughStmt(ite.thenBranch())
                    || fallsThroughStmt(ite.elseBranch().get());
        }
        if (v instanceof StmtExpr.Try tr) {
            return fallsThroughStmt(tr.body())
                    || tr.catches().stream().anyMatch(c -> fallsThroughStmt(c.body()));
        }
        return true;
    }

    /** Place {@code jump} inside the falling-through tails of a trailing compound statement; false if there is none. */
    private static boolean distributeIntoTail(List<AstNode<StmtExpr>> stmts, AstNode<StmtExpr> jump) {
        if (stmts.isEmpty()) {
            return false;
        }
        AstNode<StmtExpr> replaced = withJumpInTails(stmts.get(stmts.size() - 1), jump);
        if (replaced == null) {
            return false;
        }
        stmts.set(stmts.size() - 1, replaced);
        return true;
    }

    /**
     * {@code stmt} with a copy of {@code jump} placed at every falling-through
     * tail: the arms of an if/else (recursively, so an else-if chain -- e.g. a
     * switch cascade's bare else-if -- distributes into its leaves), the end of
     * an unlabelled block (a labelled block is a loop whose own exits land
     * right after it, so a jump inside would be skipped), and a try's body and
     * catch arms (Laurel's {@code EliminateExceptions} unwinds a {@code goto}
     * leaving a try through the finally arm before delivering it, so the back
     * edge still runs the finally exactly once). Null when the statement has
     * no falling-through tail of these shapes; the caller then appends the
     * jump after it, as before.
     */
    private static AstNode<StmtExpr> withJumpInTails(AstNode<StmtExpr> stmt, AstNode<StmtExpr> jump) {
        if (stmt.val() instanceof StmtExpr.IfThenElse ite && ite.elseBranch().isPresent()) {
            AstNode<StmtExpr> newThen = armWithJump(ite.thenBranch(), jump);
            AstNode<StmtExpr> newElse = armWithJump(ite.elseBranch().get(), jump);
            if (newThen == null && newElse == null) {
                return null;
            }
            return new AstNode<>(new StmtExpr.IfThenElse(ite.cond(),
                    newThen != null ? newThen : ite.thenBranch(),
                    Optional.of(newElse != null ? newElse : ite.elseBranch().get())), stmt.source());
        }
        if (stmt.val() instanceof StmtExpr.Block blk && blk.label().isEmpty()) {
            List<AstNode<StmtExpr>> stmts = new ArrayList<>(blk.statements());
            AstNode<StmtExpr> copy = new AstNode<>(jump.val(), jump.source());
            if (!distributeIntoTail(stmts, copy)) {
                if (!fallsThrough(blk)) {
                    return null;
                }
                stmts.add(copy);
            }
            return new AstNode<>(new StmtExpr.Block(stmts, blk.label(), blk.invariants(), blk.decreases()),
                    stmt.source());
        }
        if (stmt.val() instanceof StmtExpr.Try tr) {
            AstNode<StmtExpr> newBody = armWithJump(tr.body(), jump);
            List<CatchClause> newCatches = new ArrayList<>();
            boolean any = newBody != null;
            for (CatchClause c : tr.catches()) {
                AstNode<StmtExpr> nb = armWithJump(c.body(), jump);
                newCatches.add(nb == null ? c
                        : new CatchClause(c.binding(), c.predicate(), nb, c.bindingType()));
                any |= nb != null;
            }
            if (!any) {
                return null;
            }
            return new AstNode<>(new StmtExpr.Try(newBody != null ? newBody : tr.body(), newCatches,
                    tr.finally_()), stmt.source());
        }
        return null;
    }

    /**
     * An if-arm / try-tail with the jump placed at its end: like
     * {@link #withJumpInTails}, plus any other falling-through statement --
     * a bare statement (a switch cascade's single-statement arm is unwrapped
     * from its block), or a labelled block (a nested loop, whose exits land
     * right after it) -- becomes a two-statement block ending in the jump.
     * Null exactly when the arm cannot fall through
     * ({@link #fallsThroughStmt}): deciding by the conservative predicate
     * rather than a shape enumeration is what keeps the distribution total
     * over falling shapes (an unenumerated shape gets the jump appended
     * after it, which is where its fall-through goes, rather than silently
     * losing the back edge).
     */
    private static AstNode<StmtExpr> armWithJump(AstNode<StmtExpr> arm, AstNode<StmtExpr> jump) {
        AstNode<StmtExpr> placed = withJumpInTails(arm, jump);
        if (placed != null) {
            return placed;
        }
        if (!fallsThroughStmt(arm)) {
            return null;    // cannot fall: no tail to place the jump at
        }
        AstNode<StmtExpr> copy = new AstNode<>(jump.val(), jump.source());
        List<AstNode<StmtExpr>> stmts = new ArrayList<>(List.of(arm, copy));
        return new AstNode<>(new StmtExpr.Block(stmts, Optional.empty(), List.of(), Optional.empty()),
                arm.source());
    }

    /** Control can run off the end of the block (no trailing jump, return, or throw). */
    private static boolean fallsThrough(StmtExpr.Block b) {
        if (b.statements().isEmpty()) {
            return true;
        }
        StmtExpr lastVal = b.statements().get(b.statements().size() - 1).val();
        return !(lastVal instanceof StmtExpr.Goto) && !(lastVal instanceof StmtExpr.Return)
                && !(lastVal instanceof StmtExpr.Throw);
    }

    private static boolean endsInJump(List<AstNode<StmtExpr>> out) {
        return !out.isEmpty() && out.get(out.size() - 1).val() instanceof StmtExpr.Goto;
    }

    // --- break/continue as Laurel goto/exit ---

    AstNode<StmtExpr> loopEdge(int from, int next, boolean viaGoto) {
        if (next < 0 || !viaGoto || ctxStack.isEmpty()) {
            return null;
        }
        Ctx top = ctxStack.peek();
        if (!top.loop && next == top.exit) {
            return exit(top.label);
        }
        return jumpTo(next);
    }

    /**
     * The jump an edge to {@code target} is, if {@code target} is the header
     * (a {@code goto}, back to the start) or the exit (an {@code exit}, past
     * the end) of an enclosing loop's block; innermost loop first.
     */
    AstNode<StmtExpr> jumpTo(int target) {
        for (Ctx c : ctxStack) {
            if (!c.loop) {
                continue;
            }
            if (target == c.header) {
                return gotoStart(c.label);
            }
            if (target == c.exit) {
                return exit(c.label);
            }
        }
        return null;
    }

    AstNode<StmtExpr> exit(String label) {
        return body.e(new StmtExpr.Goto(label, new GotoDestination.BlockEnd()));
    }

    AstNode<StmtExpr> gotoStart(String label) {
        return body.e(new StmtExpr.Goto(label, new GotoDestination.BlockStart()));
    }

    // --- assert ---

    Integer tryAssert(int guardBlk, List<AstNode<StmtExpr>> out) {
        BasicBlock g = cfg.blocks.get(guardBlk);
        if (!g.isConditional()) {
            return null;
        }
        List<CodeElement> ins = elems(g);
        int n = ins.size();
        if (n < 2) {
            return null;
        }
        if (!(ins.get(n - 2) instanceof FieldInstruction fi)
                || fi.opcode() != Opcode.GETSTATIC
                || !fi.name().stringValue().equals("$assertionsDisabled")) {
            return null;
        }
        if (g.last.opcode() != Opcode.IFNE) {
            return null;
        }
        int after = g.branch, condBlk = g.fall;
        if (after < 0 || condBlk < 0 || condBlk >= cfg.blocks.size()) {
            return null;
        }
        BasicBlock c = cfg.blocks.get(condBlk);
        if (!c.isConditional() || c.branch != after) {
            return null;
        }
        int throwBlk = c.fall;
        if (throwBlk < 0 || throwBlk >= cfg.blocks.size()) {
            return null;
        }
        BasicBlock t = cfg.blocks.get(throwBlk);
        if (t.last == null || t.last.opcode() != Opcode.ATHROW || !throwsAssertionError(t)) {
            return null;
        }
        visited[guardBlk] = true;
        int mark = body.stmts.size();
        body.translateSpan(ins, elemIdxs(g), 0, n - 2);
        drainInto(out, mark);
        visited[condBlk] = true;
        AstNode<StmtExpr> cond = LaurelTrees.negate(conditionOf(c, out), ctx.method.unordered);
        visited[throwBlk] = true;
        AstNode<StmtExpr> msg = assertMessage(t);
        // Laurel's Assert has no detail-message slot; the message-bearing
        // form asserts the condition and drops the message
        // (StmtExpr.Assert(cond, empty)).
        out.add(place(body.e(new StmtExpr.Assert(cond, Optional.empty())), condBlk));
        return after;
    }

    boolean throwsAssertionError(BasicBlock t) {
        for (int idx : t.instrs) {
            if (cfg.seq.get(idx) instanceof InvokeInstruction inv
                    && inv.name().stringValue().equals("<init>")
                    && inv.owner().asInternalName().equals("java/lang/AssertionError")) {
                return true;
            }
        }
        return false;
    }

    AstNode<StmtExpr> assertMessage(BasicBlock t) {
        int mark = body.stmts.size();
        List<CodeElement> ins = elems(t);
        body.translateSpan(ins, elemIdxs(t), 0, ins.size() - 1);
        while (body.stmts.size() > mark) {
            body.stmts.remove(body.stmts.size() - 1);
        }
        return body.stack.isEmpty() ? null : body.stack.pop();
    }

    /**
     * Record which of {@code out}'s statements from {@code mark} on are
     * loop-condition lowering (see {@link #condStmts}): a statement whose
     * emission position ({@code BodyTranslator.stmtSeq}) -- or, for a
     * synthesized node like the test's {@code if} itself, the block's
     * terminator when {@code fallbackToTerminator} -- lies in the innermost
     * emitting loop's own positions. Per-statement, not per-block: a
     * for-each's hidden binding shares its basic block with the first user
     * statement.
     */
    void markCondEmitted(int blkId, List<AstNode<StmtExpr>> out, int mark, boolean fallbackToTerminator) {
        java.util.BitSet cond = activeCondPcs.peek();
        if (cond == null || cond.isEmpty() || body.seqToBci == null) {
            return;
        }
        int termSeq = fallbackToTerminator ? terminatorOf(blkId) : -1;
        for (int i = mark; i < out.size(); i++) {
            AstNode<StmtExpr> st = out.get(i);
            Integer sq = body.stmtSeq.get(st);
            int seqIdx = sq != null ? sq : termSeq;
            if (seqIdx >= 0 && seqIdx < body.seqToBci.length && cond.get(body.seqToBci[seqIdx])) {
                condStmts.add(st);
            }
        }
    }

    // --- if / if-else ---

    int emitIf(int condBlk, Set<Integer> stop, List<AstNode<StmtExpr>> out) {
        int mark = out.size();
        int next = emitIf0(condBlk, stop, out);
        markCondEmitted(condBlk, out, mark, true);
        return next;
    }

    private int emitIf0(int condBlk, Set<Integer> stop, List<AstNode<StmtExpr>> out) {
        BasicBlock c = cfg.blocks.get(condBlk);
        AstNode<StmtExpr> cond = conditionOf(c, out);
        // Refuse the CONDITION eagerly, before either branch is emitted (an
        // instanceof-on-opaque in the test beats a cast refusal inside the
        // then-branch) — a refusal order pinned by the corpus.
        body.checkPlaceable(cond);
        // The condition is evaluated once, before the branch: its implicit
        // runtime exceptions can be made explicit just ahead of the `if`.
        for (AstNode<StmtExpr> t : body.explicitImplicitThrows(cond)) {
            out.add(place(t, condBlk));
        }
        int thenE = c.fall, elseE = c.branch;
        int follow = dom.ipdom[condBlk];
        int[] exits = chainExits(condBlk, follow, stop);
        if (exits != null) {
            // `if ((a && b) || rest) S else E`: javac jumps from every operand
            // to S or to E, so E is reached from several operands. Emitting
            // each operand as its own `if` would copy E (and the operands after
            // it) into every arm, doubling the output per operand. The chain is
            // rebuilt as one expression, as for a value diamond, with reaching
            // S standing for true and reaching E for false; S and E are then
            // the two arms, emitted once each. Only the first operand's
            // implicit exceptions were hoisted above: the others are evaluated
            // conditionally and stay inside the expression.
            cond = diamondValue(c, cond, Map.of(exits[0], true, exits[1], false), follow);
            body.checkPlaceable(cond);
            thenE = exits[0];
            elseE = exits[1];
        }

        Set<Integer> sub = plus(stop, follow);
        // An arm that leaves the region for an enclosing loop's header or exit,
        // other than by reaching the join, is that loop's goto/exit.
        AstNode<StmtExpr> thenJ = armJump(thenE, follow, stop);
        AstNode<StmtExpr> elseJ = armJump(elseE, follow, stop);
        if (thenJ != null && elseJ != null) {
            out.add(place(body.e(new StmtExpr.IfThenElse(cond, blockOf(List.of(thenJ)),
                    Optional.of(blockOf(List.of(elseJ))))), condBlk));
            return follow;
        }
        if (elseJ != null) {
            out.add(place(body.e(new StmtExpr.IfThenElse(LaurelTrees.negate(cond, ctx.method.unordered),
                    blockOf(List.of(elseJ)), Optional.empty())), condBlk));
            return thenE;
        }
        if (thenJ != null) {
            out.add(place(body.e(new StmtExpr.IfThenElse(cond, blockOf(List.of(thenJ)), Optional.empty())),
                    condBlk));
            return elseE;
        }
        if (elseE == follow || elseE < 0 || stop.contains(elseE)) {
            List<AstNode<StmtExpr>> then = region(thenE, sub);
            out.add(place(body.e(new StmtExpr.IfThenElse(cond, blockOf(then), Optional.empty())), condBlk));
        } else if (thenE == follow || stop.contains(thenE)) {
            List<AstNode<StmtExpr>> els = region(elseE, sub);
            out.add(place(body.e(new StmtExpr.IfThenElse(LaurelTrees.negate(cond, ctx.method.unordered),
                    blockOf(els), Optional.empty())), condBlk));
        } else {
            List<AstNode<StmtExpr>> then = region(thenE, sub);
            List<AstNode<StmtExpr>> els = region(elseE, sub);
            out.add(place(body.e(new StmtExpr.IfThenElse(cond, blockOf(then),
                    Optional.of(blockOf(els)))), condBlk));
        }
        return follow;
    }

    /**
     * The two blocks that the chain of condition blocks headed by
     * {@code condBlk} decides between, in bytecode order (javac emits the then
     * branch first), or null when {@code condBlk} does not head such a chain:
     * fewer than two condition blocks, or not exactly two exits. A chain block
     * is a condition block of the same source statement as the head (it does
     * not start a statement of its own, so a nested {@code if}, an
     * {@code assert} or a call statement before an inner {@code if} stays a
     * statement), with no statements inside, that is not the join
     * {@code follow}, not a block in {@code stop}, not a loop header, not the
     * start of a try and not a boolean diamond; every other block the chain
     * jumps to is an exit. A chain is also refused when a later operand's
     * implicit exception (a division's ArithmeticException, a cast's
     * ClassCastException) would be made an explicit throw here, because that
     * throw is only emitted as a statement in front of an {@code if}: those
     * operands keep their own {@code if}s, as before.
     */
    private int[] chainExits(int condBlk, int follow, Set<Integer> stop) {
        Set<Integer> chain = new HashSet<>();
        Set<Integer> exits = new TreeSet<>();
        Deque<Integer> work = new ArrayDeque<>();
        work.push(condBlk);
        while (!work.isEmpty()) {
            int id = work.pop();
            if (chain.contains(id) || exits.contains(id)) {
                continue;
            }
            BasicBlock b = id >= 0 ? cfg.blocks.get(id) : null;
            boolean inChain = id == condBlk
                    || (b != null && b.isConditional() && !b.isSwitch() && id != follow && !stop.contains(id)
                        && !dom.isLoopHeader(id) && !triesByStart.containsKey(id) && !blockHasStatement(b)
                        && booleanDiamond(id) == null && isOperandBlock(b));
            if (!inChain) {
                exits.add(id);
                continue;
            }
            if (id != condBlk && operandThrowsExplicitly(b)) {
                return null;
            }
            chain.add(id);
            work.push(b.fall);
            work.push(b.branch);
        }
        if (chain.size() < 2 || exits.size() != 2) {
            return null;
        }
        int[] result = new int[2];
        int i = 0;
        for (int exit : exits) {
            result[i++] = exit;
        }
        return result;
    }

    /**
     * Whether condition block {@code b} is an operand of the statement that
     * started before it, rather than the start of a statement of its own, and
     * one that can live inside a condition expression. With a
     * CharacterRangeTable the statement test is exact; without one it falls
     * back to the LineNumberTable, and a block that pops a value, increments
     * a local or reads {@code $assertionsDisabled} is a statement
     * ({@code foo();}, {@code i++;}, {@code assert}). An operand that
     * allocates an array keeps its own {@code if}: an allocation inside a
     * condition expression is refused, in front of its own {@code if} it is
     * a statement as before.
     */
    private boolean isOperandBlock(BasicBlock b) {
        if (b.instrs.isEmpty() || startsOwnStatement(b.instrs.get(0))) {
            return false;
        }
        boolean noCrt = body.crt == null || body.crt.isEmpty();
        for (int i : b.instrs) {
            CodeElement in = cfg.seq.get(i);
            if (in instanceof NewPrimitiveArrayInstruction || in instanceof NewReferenceArrayInstruction
                    || in instanceof NewMultiArrayInstruction) {
                return false;
            }
            if (noCrt && (in instanceof IncrementInstruction
                    || (in instanceof StackInstruction s && (s.opcode() == Opcode.POP || s.opcode() == Opcode.POP2))
                    || (in instanceof FieldInstruction fi
                        && fi.name().stringValue().equals("$assertionsDisabled")))) {
                return false;
            }
        }
        return true;
    }

    /** Whether an implicit exception of an operation in {@code b} is made an explicit throw in this method. */
    private boolean operandThrowsExplicitly(BasicBlock b) {
        for (int i : b.instrs) {
            CodeElement in = cfg.seq.get(i);
            if (in instanceof Instruction ins) {
                switch (ins.opcode()) {
                    case IDIV, IREM, LDIV, LREM -> {
                        if (body.mayThrowHere(BodyTranslator.ARITHMETIC_EXCEPTION)) {
                            return true;
                        }
                    }
                    case CHECKCAST -> {
                        if (body.mayThrowHere(BodyTranslator.CLASS_CAST_EXCEPTION)) {
                            return true;
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        return false;
    }

    private AstNode<StmtExpr> armJump(int target, int follow, Set<Integer> stop) {
        return target >= 0 && target != follow && stop.contains(target) ? jumpTo(target) : null;
    }

    // --- diamonds / ternaries ---

    Integer booleanDiamond(int condBlk) {
        BasicBlock c = cfg.blocks.get(condBlk);
        if (!c.isConditional()) {
            return null;
        }
        int merge = dom.ipdom[condBlk];
        if (merge < 0) {
            return null;
        }
        BasicBlock t = cfg.blocks.get(c.fall), f = cfg.blocks.get(c.branch);
        boolean tTrue = ops(t).equals(List.of(Opcode.ICONST_1, Opcode.GOTO)) && only(t.succ, merge);
        boolean fFalse = ops(f).equals(List.of(Opcode.ICONST_0)) && only(f.succ, merge);
        return (tTrue && fFalse) ? merge : null;
    }

    void emitDiamondValue(BasicBlock c, List<AstNode<StmtExpr>> out) {
        visited[c.fall] = true;
        visited[c.branch] = true;
        AstNode<StmtExpr> v = conditionOf(c, out);
        body.bools.add(v);
        body.stack.push(v);
    }

    Integer valueDiamond(int condBlk) {
        BasicBlock c = cfg.blocks.get(condBlk);
        if (!c.isConditional()) {
            return null;
        }
        int merge = dom.ipdom[condBlk];
        if (merge < 0 || merge == condBlk || c.fall == merge || c.branch == merge) {
            return null;
        }
        if (armIsValue(c.fall, merge, new HashSet<>()) && armIsValue(c.branch, merge, new HashSet<>())) {
            return merge;
        }
        return null;
    }

    boolean armIsValue(int entry, int merge, Set<Integer> seen) {
        return armIsValue(entry, merge, seen, new HashMap<>());
    }

    /**
     * {@code memo} caches the answer per (entry, merge) for one top-level call.
     * The walk recurses into both arms of every condition, and in a
     * short-circuit chain both arms of each operand continue into the same
     * rest of the chain, so without the memo a chain of n operands is walked
     * 2^n times. Within one call {@link #skipLead} is fixed, so the cached
     * answers stay valid; {@code seen} only guards against cycles, which
     * javac never puts inside an expression.
     */
    private boolean armIsValue(int entry, int merge, Set<Integer> seen, Map<Long, Boolean> memo) {
        long key = ((long) entry << 32) | (merge & 0xffffffffL);
        Boolean known = memo.get(key);
        if (known != null) {
            return known;
        }
        boolean result = armIsValue0(entry, merge, seen, memo);
        memo.put(key, result);
        return result;
    }

    private boolean armIsValue0(int entry, int merge, Set<Integer> seen, Map<Long, Boolean> memo) {
        int cur = entry;
        while (cur != merge) {
            if (cur < 0 || !seen.add(cur)) {
                return false;
            }
            BasicBlock b = cfg.blocks.get(cur);
            if (b.isSwitch() || blockHasStatement(b)) {
                return false;
            }
            if (b.isConditional()) {
                Integer bd = booleanDiamond(cur);
                if (bd != null) {
                    cur = bd;
                    continue;
                }
                int m = dom.ipdom[cur];
                if (m < 0 || m == cur) {
                    return false;
                }
                if (!armIsValue(b.fall, m, new HashSet<>(seen), memo)
                        || !armIsValue(b.branch, m, new HashSet<>(seen), memo)) {
                    return false;
                }
                cur = m;
            } else {
                cur = b.gotoTarget >= 0 ? b.gotoTarget : b.fall;
            }
        }
        return true;
    }

    boolean blockHasStatement(BasicBlock b) {
        for (int i = skipLead.getOrDefault(b.id, 0); i < b.instrs.size(); i++) {
            Instruction in = (Instruction) cfg.seq.get(b.instrs.get(i));
            if (in instanceof IncrementInstruction inc) {
                if (incAbsorbableInBlock(b, i, inc)) {
                    continue;
                }
                return true;
            }
            if (in instanceof StoreInstruction || in instanceof java.lang.classfile.instruction.ArrayStoreInstruction
                    || in instanceof java.lang.classfile.instruction.ReturnInstruction
                    || in.opcode() == Opcode.ATHROW) {
                return true;
            }
            if (in instanceof InvokeInstruction inv
                    && inv.typeSymbol().returnType().descriptorString().equals("V")) {
                return true;
            }
            if (in instanceof FieldInstruction fi
                    && (fi.opcode() == Opcode.PUTFIELD || fi.opcode() == Opcode.PUTSTATIC)) {
                return true;
            }
        }
        return false;
    }

    private boolean incAbsorbableInBlock(BasicBlock b, int at, IncrementInstruction inc) {
        int c = inc.constant();
        if (c != 1 && c != -1) {
            return false;
        }
        for (int j = skipLead.getOrDefault(b.id, 0); j < b.instrs.size(); j++) {
            if (j == at) {
                continue;
            }
            if (cfg.seq.get(b.instrs.get(j)) instanceof LoadInstruction li && li.slot() == inc.slot()) {
                return true;
            }
        }
        return false;
    }

    void foldChainPatternBind(int condBlk) {
        BasicBlock c = cfg.blocks.get(condBlk);
        if (!c.isConditional() || c.last.opcode() != Opcode.IFEQ) {
            return;
        }
        int n = c.instrs.size();
        if (n < 2 || !(insAt(c, n - 2) instanceof TypeCheckInstruction ti)
                || ti.opcode() != Opcode.INSTANCEOF) {
            return;
        }
        int fb = c.fall;
        if (fb < 0 || fb >= cfg.blocks.size() || skipLead.containsKey(fb)) {
            return;
        }
        BasicBlock b = cfg.blocks.get(fb);
        if (b.instrs.size() < 4) {
            return;
        }
        if (!(insAt(b, 0) instanceof LoadInstruction)) {
            return;
        }
        if (!(insAt(b, 1) instanceof TypeCheckInstruction cc) || cc.opcode() != Opcode.CHECKCAST) {
            return;
        }
        if (!(insAt(b, 2) instanceof StoreInstruction st)) {
            return;
        }
        skipLead.put(fb, 3);
        if (valueDiamond(condBlk) == null) {
            skipLead.remove(fb);
        }
    }

    void emitValueDiamond(int condBlk, int merge, List<AstNode<StmtExpr>> out) {
        BasicBlock c = cfg.blocks.get(condBlk);
        AstNode<StmtExpr> cond = conditionOf(c, out);
        // A pattern-bind instanceof (foldChainPatternBind) cannot carry a
        // binding in Laurel; the IsType/deferred marker refuses at placement,
        // so an instanceof with a pattern refuses the method.
        body.stack.push(diamondValue(c, cond, Map.of(), merge));
    }

    // --- shared tails of short-circuit chains ---

    /**
     * The value of the diamond at condition block {@code c}, whose condition
     * {@code cond} is already translated; every path from an arm ends at
     * {@code merge} or at a block in {@code tails}.
     *
     * <p>javac compiles {@code (a && b) || rest} to jumps in which the block of
     * {@code rest} is reached both when {@code a} fails and when {@code b}
     * fails: it has two predecessors inside the diamond. Walking each arm to
     * {@code merge} translates {@code rest} once per arm, so a chain of n such
     * disjuncts doubles the Laurel program with every disjunct. Here a block
     * reached by both arms is translated once and re-attached with the
     * value the bytecode encodes: when every leaf the arms reach without
     * passing through it is {@code iconst_1}, the diamond is
     * {@code if head then 1 else rest}; when every such leaf is {@code iconst_0},
     * it is {@code if head then rest else 0}. {@code head} is this diamond with
     * reaching the shared block standing for the complementary literal, so it
     * collapses to the chain before {@code rest}. {@link #buildTernary} then
     * folds the ternary to {@code head || rest} or {@code head && rest} when
     * {@code rest} is boolean, and keeps an {@code if} when it is not (the
     * chain was the condition of an int-valued ternary). For
     * {@code ite(c, X, Y)} with every leaf of X and Y either {@code 1} or the
     * shared block, this is the identity
     * {@code ite(c, X[rest], Y[rest]) = ite(ite(c, X[false], Y[false]), 1, rest)}
     * (and dually for {@code 0}); the arms are still translated before the
     * shared block, so evaluation order and short-circuiting are those of the
     * bytecode. Shapes that are not chains of comparisons into literal sinks
     * take the plain ternary, as before.
     *
     * <p>A chain of n operands has n shared blocks, one after the other. They
     * are handled in a loop, not by recursing from each {@code rest} into the
     * next, so the stack depth does not grow with the chain; the recursion
     * into {@code head} only nests as deep as shared blocks nest.
     *
     * @param tails blocks outside this diamond that an arm may reach, each
     *              mapped to the literal the enclosing factoring made it stand
     *              for on this path
     */
    private AstNode<StmtExpr> diamondValue(BasicBlock c, AstNode<StmtExpr> cond, Map<Integer, Boolean> tails,
                                           int merge) {
        /** The value of the leaves other than the shared block: a literal, or the translated block they all reach. */
        record Step(AstNode<StmtExpr> head, Leaf leaf, AstNode<StmtExpr> leafValue, int stmtsMark) {
        }
        List<Step> steps = new ArrayList<>();
        AstNode<StmtExpr> last = null;
        boolean inRest = false;
        try {
            while (last == null) {
                Step step = null;
                for (int shared : sharedBlocks(c, tails, merge)) {
                    Leaf leaf = commonLeaf(c, shared, tails, merge);
                    if (leaf == null) {
                        continue;
                    }
                    Map<Integer, Boolean> inner = new HashMap<>(tails);
                    AstNode<StmtExpr> leafValue = null;
                    if (leaf.literal() != null) {
                        inner.put(shared, !leaf.literal());
                    } else {
                        inner.put(leaf.block(), true);
                        inner.put(shared, false);
                    }
                    AstNode<StmtExpr> head = diamondValue(c, cond, inner, merge);
                    if (!inRest) {
                        // The leaf block and the rest run only when the head decided: conditionally, like arms.
                        body.enterConditional();
                        inRest = true;
                    }
                    if (leaf.literal() == null) {
                        leafValue = armValue(leaf.block(), tails, merge);
                    }
                    step = new Step(head, leaf, leafValue, body.stmts.size());
                    steps.add(step);
                    BasicBlock j = cfg.blocks.get(shared);
                    int m = dom.ipdom[shared];
                    boolean closed = m >= 0 && m != merge && !tails.containsKey(m);
                    if (!j.isConditional() || booleanDiamond(shared) != null || closed) {
                        // Not a condition whose paths end where this diamond's do: the rest is an arm.
                        last = evalArm0(shared, tails, merge);
                    } else {
                        cond = translateCondition(j);
                        c = j;
                    }
                    break;
                }
                if (step == null) {
                    last = buildTernary(cond, armValue(c.fall, tails, merge), armValue(c.branch, tails, merge));
                }
            }
        } finally {
            if (inRest) {
                body.exitConditional();
            }
        }
        AstNode<StmtExpr> acc = last;
        for (int i = steps.size() - 1; i >= 0; i--) {
            Step step = steps.get(i);
            acc = withArmStatements(acc, step.stmtsMark());
            if (step.leaf().literal() == null) {
                acc = buildTernary(step.head(), step.leafValue(), acc);
            } else {
                AstNode<StmtExpr> lit = body.e(body.pinnedLongLiteral(step.leaf().literal() ? 1 : 0));
                body.noteDesc(lit, "I");
                acc = step.leaf().literal() ? buildTernary(step.head(), lit, acc) : buildTernary(step.head(), acc, lit);
            }
        }
        return acc;
    }

    /**
     * What a leaf of a diamond stands for: the literal of an {@code iconst_1} /
     * {@code iconst_0} sink or of a tail, or else the block the leaf is (a
     * value block such as the {@code 5} of {@code chain ? 5 : 7}, or a
     * statement block the chain of an {@code if} decides for).
     */
    private record Leaf(int block, Boolean literal) {
        static Leaf of(boolean literal) {
            return new Leaf(-1, literal);
        }

        static Leaf of(int block) {
            return new Leaf(block, null);
        }
    }

    /** Translate the instructions of condition block {@code c} before its jump and materialize the jump's comparison. */
    private AstNode<StmtExpr> translateCondition(BasicBlock c) {
        visited[c.id] = true;
        body.pos = c.instrs.isEmpty() ? body.pos : c.instrs.get(0);
        List<CodeElement> ins = elems(c);
        body.translateSpan(ins, elemIdxs(c), 0, ins.size() - 1);
        return body.materializeComparison(c.last.opcode());
    }


    /**
     * The blocks reachable from an arm of {@code c} without entering
     * {@code merge} or a tail, as candidates for the block to factor out. A
     * block reached from one arm only is a valid candidate too (the other
     * arm's leaves are then the ones that must agree): it lets a chain with
     * no shared block, {@code a && b && c}, be handled in the loop rather than
     * by recursing once per operand. Candidates whose own value the chain
     * decides for come first, last in bytecode order first: value blocks other
     * than {@code iconst_1}/{@code iconst_0} sinks, and conditions reached from
     * both arms whose arms are not conditions (a ternary of its own, the
     * {@code x} of {@code chain ? x : y}). With them first, the whole chain
     * becomes the condition of one {@code if}, and javac puts {@code y} after
     * {@code x}. Then the condition blocks, nearest first. Literal sinks are
     * leaves, never candidates: translating a chain as
     * {@code if chain then 1 else 0} would lose its boolean type.
     */
    private List<Integer> sharedBlocks(BasicBlock c, Map<Integer, Boolean> tails, int merge) {
        Set<Integer> stop = new HashSet<>(tails.keySet());
        stop.add(merge);
        if (isLeafFor(c.fall, stop) && isLeafFor(c.branch, stop)) {
            // Both arms are leaves: the plain ternary is already as small as it gets, and
            // factoring would only reorder it (`if d <= 0 then 1 else 2` for `d > 0 ? 2 : 1`).
            return List.of();
        }
        Set<Integer> fromFall = reachable(c.fall, stop);
        Set<Integer> fromBranch = reachable(c.branch, stop);
        Set<Integer> all = new HashSet<>(fromFall);
        all.addAll(fromBranch);
        List<Integer> values = new ArrayList<>();
        List<Integer> conditions = new ArrayList<>();
        for (int b : all) {
            BasicBlock block = cfg.blocks.get(b);
            if (block.isConditional()) {
                if (isTerminalCondition(b)) {
                    values.add(b);
                } else {
                    conditions.add(b);
                }
            } else if (!block.isSwitch() && literalSink(block, merge) == null) {
                values.add(b);
            }
        }
        values.sort(Collections.reverseOrder());
        Collections.sort(conditions);
        values.addAll(conditions);
        return values;
    }

    /** Whether block {@code id} ends a path of the diamond: a stop (tail or merge), a non-condition, or a terminal condition. */
    private boolean isLeafFor(int id, Set<Integer> stop) {
        return id < 0 || stop.contains(id) || !cfg.blocks.get(id).isConditional() || isTerminalCondition(id);
    }

    /**
     * A condition block whose arms are not conditions and are reached only
     * through it: a ternary of its own that the chain decides for (such as
     * {@code d > 0 ? 5 : 7} in {@code chain ? (d > 0 ? 5 : 7) : 0}), not an
     * operand of the chain. Treated as a leaf, like a value block. The last
     * operand of a chain is not one: its sinks are reached from the other
     * operands too.
     */
    private boolean isTerminalCondition(int b) {
        BasicBlock block = cfg.blocks.get(b);
        if (!block.isConditional()) {
            return false;
        }
        for (int s : List.of(block.fall, block.branch)) {
            BasicBlock succ = cfg.blocks.get(s);
            if (succ.isConditional() || succ.preds.size() != 1) {
                return false;
            }
        }
        return true;
    }

    /** The blocks reachable from {@code entry} (itself included) without entering a block in {@code stop}. */
    private Set<Integer> reachable(int entry, Set<Integer> stop) {
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> work = new ArrayDeque<>();
        work.push(entry);
        while (!work.isEmpty()) {
            int b = work.pop();
            if (b < 0 || stop.contains(b) || !seen.add(b)) {
                continue;
            }
            for (int s : cfg.blocks.get(b).succ) {
                work.push(s);
            }
        }
        return seen;
    }

    /**
     * What every leaf reachable from the arms of {@code c} without entering
     * {@code shared} stands for: a tail's literal, a literal sink's constant,
     * or one and the same block. Null when the leaves disagree, when there is
     * none, or when a condition on the way jumps straight to {@code merge}:
     * the shape is then not a short-circuit chain deciding between two
     * outcomes.
     */
    private Leaf commonLeaf(BasicBlock c, int shared, Map<Integer, Boolean> tails, int merge) {
        Leaf common = null;
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> work = new ArrayDeque<>();
        work.push(c.fall);
        work.push(c.branch);
        while (!work.isEmpty()) {
            int id = work.pop();
            if (id == shared || !seen.add(id)) {
                continue;
            }
            Leaf leaf;
            Boolean tail = tails.get(id);
            if (tail != null) {
                leaf = Leaf.of(tail);
            } else if (id < 0 || id == merge) {
                return null;    // a condition that jumps straight to the merge carries no value
            } else {
                BasicBlock b = cfg.blocks.get(id);
                if (b.isConditional() && !isTerminalCondition(id)) {
                    work.push(b.fall);
                    work.push(b.branch);
                    continue;
                }
                if (b.isSwitch()) {
                    return null;
                }
                Boolean literal = b.isConditional() ? null : literalSink(b, merge);
                leaf = literal != null ? Leaf.of(literal) : Leaf.of(id);
            }
            if (common != null && !common.equals(leaf)) {
                return null;
            }
            common = leaf;
        }
        return common;
    }

    /** The constant of a literal sink, {@code iconst_1 [goto]} or {@code iconst_0 [goto]} into {@code merge}; else null. */
    private Boolean literalSink(BasicBlock b, int merge) {
        if (!only(b.succ, merge)) {
            return null;
        }
        List<Opcode> o = ops(b);
        if (o.size() == 2 && o.get(1) == Opcode.GOTO) {
            o = o.subList(0, 1);
        }
        if (o.equals(List.of(Opcode.ICONST_1))) {
            return true;
        }
        if (o.equals(List.of(Opcode.ICONST_0))) {
            return false;
        }
        return null;
    }

    private AstNode<StmtExpr> boolLiteral(boolean value) {
        AstNode<StmtExpr> lit = body.e(new StmtExpr.LiteralBool(value));
        body.noteDesc(lit, "Z");
        body.bools.add(lit);
        return lit;
    }

    AstNode<StmtExpr> armValue(int entry, Map<Integer, Boolean> tails, int merge) {
        int mark = body.stmts.size();
        return withArmStatements(evalArm(entry, tails, merge), mark);
    }

    /** Arm value {@code v} together with the statements emitted since {@code mark}, as a block if there are any. */
    private AstNode<StmtExpr> withArmStatements(AstNode<StmtExpr> v, int mark) {
        if (body.stmts.size() == mark) {
            return v;
        }
        var stmts = new ArrayList<>(body.stmts.subList(mark, body.stmts.size()));
        body.stmts.subList(mark, body.stmts.size()).clear();
        stmts.add(v);
        AstNode<StmtExpr> block = body.e(new StmtExpr.Block(stmts, Optional.empty(), List.of(), Optional.empty()));
        if (isBoolish(v)) {
            body.bools.add(block);
        }
        if (body.heapReadValues.contains(v)) {
            body.heapReadValues.add(block);
        }
        return block;
    }

    AstNode<StmtExpr> buildTernary(AstNode<StmtExpr> cond, AstNode<StmtExpr> thenVal, AstNode<StmtExpr> elseVal) {
        boolean boolish = isBoolish(thenVal) || isBoolish(elseVal);
        if (boolish) {
            thenVal = body.coerce(thenVal, "Z");
            elseVal = body.coerce(elseVal, "Z");
        }
        AstNode<StmtExpr> r;
        if (boolish && LaurelTrees.isTrueLit(thenVal.val()) && LaurelTrees.isFalseLit(elseVal.val())) {
            r = cond;
        } else if (boolish && LaurelTrees.isFalseLit(thenVal.val()) && LaurelTrees.isTrueLit(elseVal.val())) {
            r = LaurelTrees.negate(cond, ctx.method.unordered);
        } else if (boolish && LaurelTrees.isFalseLit(elseVal.val())) {
            r = body.e(LaurelFactory.primitiveOp(new Operation.AndThen(), cond, thenVal));
        } else if (boolish && LaurelTrees.isTrueLit(thenVal.val())) {
            r = body.e(LaurelFactory.primitiveOp(new Operation.OrElse(), cond, elseVal));
        } else if (boolish && LaurelTrees.isTrueLit(elseVal.val())) {
            r = body.e(LaurelFactory.primitiveOp(new Operation.OrElse(),
                    LaurelTrees.negate(cond, ctx.method.unordered), thenVal));
        } else if (boolish && LaurelTrees.isFalseLit(thenVal.val())) {
            r = body.e(LaurelFactory.primitiveOp(new Operation.AndThen(),
                    LaurelTrees.negate(cond, ctx.method.unordered), elseVal));
        } else {
            r = body.e(new StmtExpr.IfThenElse(cond, thenVal, Optional.of(elseVal)));
        }
        if (boolish) {
            body.bools.add(r);
        }
        // Either arm may be a box read from the heap, so the result may be null.
        if (body.heapReadValues.contains(thenVal) || body.heapReadValues.contains(elseVal)) {
            body.heapReadValues.add(r);
        }
        if (body.arrays.enabled()) {
            typeArrayTernary(r, thenVal, elseVal);
        }
        // A ternary whose arms are both @Unbounded stays mathematical, so
        // `(c ? u + 1 : u) * 2` carries no int32 obligation. Both arms, not
        // either: a bounded arm's value must keep its overflow checks.
        if (body.isUnboundedInt(thenVal) && body.isUnboundedInt(elseVal)) {
            body.unboundedResults.add(r);
        }
        return r;
    }

    /**
     * An array-valued conditional expression: both arms must have the same
     * array descriptor, which the result then carries (so its element kind
     * is known); arms of different types make it an Object use. An arm that
     * already carries its own refusal is left to it.
     */
    private void typeArrayTernary(AstNode<StmtExpr> r, AstNode<StmtExpr> thenVal, AstNode<StmtExpr> elseVal) {
        String td = body.descOf.get(thenVal);
        String ed = body.descOf.get(elseVal);
        boolean thenArray = td != null && td.startsWith("[");
        boolean elseArray = ed != null && ed.startsWith("[");
        if (!thenArray && !elseArray) {
            return;
        }
        if (body.deferredRefusal.containsKey(thenVal) || body.deferredRefusal.containsKey(elseVal)) {
            return;
        }
        if (!java.util.Objects.equals(td, ed)) {
            throw new JavaViolationException(ArrayModel.AS_OBJECT);
        }
        body.noteDesc(r, td);
        TypeRef t = body.staticTypeOf.get(thenVal);
        if (t != null) {
            body.staticTypeOf.put(r, t);
        }
    }

    AstNode<StmtExpr> evalArm(int entry, int merge) {
        return evalArm(entry, Map.of(), merge);
    }

    AstNode<StmtExpr> evalArm(int entry, Map<Integer, Boolean> tails, int merge) {
        // A value arm runs only on its side of the condition.
        body.enterConditional();
        try {
            return evalArm0(entry, tails, merge);
        } finally {
            body.exitConditional();
        }
    }

    private AstNode<StmtExpr> evalArm0(int entry, Map<Integer, Boolean> tails, int merge) {
        int cur = entry;
        while (true) {
            Boolean tail = tails.get(cur);
            if (tail != null) {
                // A block the enclosing diamondValue translates once (or, for the
                // chain of an `if`, a branch it emits as a statement): on this path
                // the chain so far has the literal it stands for.
                return boolLiteral(tail);
            }
            if (cur == merge) {
                break;
            }
            visited[cur] = true;
            BasicBlock b = cfg.blocks.get(cur);
            body.pos = b.instrs.isEmpty() ? body.pos : b.instrs.get(0);
            if (b.isConditional()) {
                Integer bd = booleanDiamond(cur);
                if (bd != null) {
                    visited[b.fall] = true;
                    visited[b.branch] = true;
                    List<CodeElement> ins = elems(b);
                    body.translateSpan(ins, elemIdxs(b), 0, ins.size() - 1);
                    AstNode<StmtExpr> v = body.materializeComparison(b.last.opcode());
                    body.bools.add(v);
                    body.stack.push(v);
                    cur = bd;
                } else {
                    int m = dom.ipdom[cur];
                    if (m >= 0 && m != merge && !tails.containsKey(m)) {
                        // A ternary closed inside the arm: its value is then used by what follows.
                        if (!tails.isEmpty()) {
                            for (int reached : reachable(cur, Set.of(m))) {
                                if (tails.containsKey(reached)) {
                                    // Its value would be a tail's literal and the walk would go on past the tail.
                                    throw new StructException("a shared block of a short-circuit chain inside a ternary");
                                }
                            }
                        }
                        body.stack.push(evalTernaryValue(cur, tails, m));
                        cur = m;
                    } else {
                        // Its paths end where the arm's do: it is the rest of the arm.
                        return evalTernaryValue(cur, tails, merge);
                    }
                }
            } else {
                List<CodeElement> ins = elems(b);
                body.translateSpan(ins, elemIdxs(b), 0, ins.size());
                cur = b.gotoTarget >= 0 ? b.gotoTarget : b.fall;
            }
        }
        return body.stack.pop();
    }

    AstNode<StmtExpr> evalTernaryValue(int condBlk, Map<Integer, Boolean> tails, int merge) {
        BasicBlock c = cfg.blocks.get(condBlk);
        return diamondValue(c, translateCondition(c), tails, merge);
    }

    boolean isBoolish(AstNode<StmtExpr> e) {
        return body.bools.contains(e) || LaurelTrees.isBooleanish(e.val());
    }

    // --- block helpers ---

    void translateBlock(BasicBlock b, List<AstNode<StmtExpr>> out) {
        int mark = body.stmts.size();
        List<CodeElement> ins = elems(b);
        body.translateInstrs(ins, elemIdxs(b));
        drainInto(out, mark);
    }

    List<CodeElement> elems(BasicBlock b) {
        List<CodeElement> l = new ArrayList<>();
        int end = b.instrs.size() - skipTail.getOrDefault(b.id, 0);
        for (int i = skipLead.getOrDefault(b.id, 0); i < end; i++) {
            l.add(cfg.seq.get(b.instrs.get(i)));
        }
        return l;
    }

    List<Integer> elemIdxs(BasicBlock b) {
        List<Integer> l = new ArrayList<>();
        int end = b.instrs.size() - skipTail.getOrDefault(b.id, 0);
        for (int i = skipLead.getOrDefault(b.id, 0); i < end; i++) {
            l.add(b.instrs.get(i));
        }
        return l;
    }

    AstNode<StmtExpr> conditionOf(BasicBlock b, List<AstNode<StmtExpr>> preOut) {
        int mark = body.stmts.size();
        List<CodeElement> ins = elems(b);
        body.translateSpan(ins, elemIdxs(b), 0, ins.size() - 1);
        drainInto(preOut, mark);
        return body.materializeComparison(b.last.opcode());
    }

    void drainInto(List<AstNode<StmtExpr>> out, int mark) {
        for (int i = mark; i < body.stmts.size(); i++) {
            out.add(body.stmts.get(i));
        }
        while (body.stmts.size() > mark) {
            body.stmts.remove(body.stmts.size() - 1);
        }
    }

    /** A Laurel block of these statements (used for if/loop arms). */
    AstNode<StmtExpr> blockOf(List<AstNode<StmtExpr>> stmts) {
        return body.e(new StmtExpr.Block(new ArrayList<>(stmts), Optional.empty(), List.of(), Optional.empty()));
    }

    Instruction insAt(BasicBlock b, int k) {
        return (Instruction) cfg.seq.get(b.instrs.get(k));
    }

    List<Opcode> ops(BasicBlock b) {
        List<Opcode> o = new ArrayList<>();
        for (int i : b.instrs) {
            o.add(((Instruction) cfg.seq.get(i)).opcode());
        }
        return o;
    }

    static boolean only(List<Integer> succ, int x) {
        return succ.size() == 1 && succ.get(0) == x;
    }

    int firstExit(Set<Integer> bodyBlocks) {
        for (int bId : bodyBlocks) {
            for (int s : cfg.blocks.get(bId).succ) {
                if (!bodyBlocks.contains(s)) {
                    return s;
                }
            }
        }
        return -1;
    }

    void markChainVisited(int from, int to) {
        Set<Integer> seen = new HashSet<>();
        int c = from;
        while (c >= 0 && c != to && c < cfg.blocks.size() && seen.add(c)) {
            visited[c] = true;
            BasicBlock b = cfg.blocks.get(c);
            c = b.gotoTarget >= 0 ? b.gotoTarget : b.fall;
        }
    }

    static Set<Integer> plus(Set<Integer> s, int x) {
        if (x < 0) {
            return s;
        }
        Set<Integer> r = new HashSet<>(s);
        r.add(x);
        return r;
    }

    /** Stamp a structural statement with its controlling block's terminator span. */
    AstNode<StmtExpr> place(AstNode<StmtExpr> raw, int blkId) {
        int seqIdx = terminatorOf(blkId);
        FileRange range = seqIdx < 0
                ? ctx.src.defaultRange
                : body.stmtRange(seqIdx, false);
        return body.place(raw, range);
    }

    int terminatorOf(int blkId) {
        if (blkId < 0 || blkId >= cfg.blocks.size()) {
            return -1;
        }
        BasicBlock b = cfg.blocks.get(blkId);
        return b.instrs.isEmpty() ? -1 : b.instrs.get(b.instrs.size() - 1);
    }

    Span spanOfNode(AstNode<StmtExpr> n) {
        // Recover the CRT span from the node's stamped FileRange line/col.
        FileRange r = n.source();
        if (r == SourceMap.UNKNOWN_RANGE) {
            return Span.NONE;
        }
        var start = PositionCodec.decode(r.range().start().byteIdx());
        var stop = PositionCodec.decode(r.range().stop().byteIdx());
        return new Span(start.line(), start.character(), stop.line(), stop.character());
    }


    // --- switch expression (value-producing switch) ---


    /** Follow a straight-line chain from {@code b}, returning where it exits (goto target / fall). */
    int chainExit(int b) {
        Set<Integer> seen = new HashSet<>();
        int c = b;
        while (c >= 0 && c < cfg.blocks.size() && seen.add(c)) {
            BasicBlock blk = cfg.blocks.get(c);
            if (blk.gotoTarget >= 0) {
                return blk.gotoTarget;
            }
            if (blk.succ.size() == 1 && blk.fall >= 0) {
                c = blk.fall;
                continue;
            }
            return blk.fall;
        }
        return -1;
    }

    void markHandlerVisited(int hb) {
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> q = new ArrayDeque<>();
        q.add(hb);
        while (!q.isEmpty()) {
            int c = q.poll();
            if (c < 0 || !seen.add(c)) {
                continue;
            }
            visited[c] = true;
            BasicBlock b = cfg.blocks.get(c);
            if (b.last != null && b.last.opcode() == Opcode.ATHROW) {
                continue;
            }
            for (int s : b.succ) {
                q.add(s);
            }
        }
    }

    boolean isPureGoto(BasicBlock b) {
        return b.instrs.size() == 1 && b.last instanceof BranchInstruction br && br.opcode() == Opcode.GOTO;
    }


}
