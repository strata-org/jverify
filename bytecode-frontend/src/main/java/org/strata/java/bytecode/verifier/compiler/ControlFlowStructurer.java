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
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The control-flow structurer: the region walker over the CFG, emitting
 * Laurel {@code AstNode<StmtExpr>} directly. Handles if/else, ternary and
 * boolean diamonds, and asserts itself; loops, switches, and
 * try/catch/finally/synchronized are delegated to {@link LoopEmitter},
 * {@link SwitchEmitter}, and {@link TryEmitter}, which share this walker's
 * region/placement services and visited-set bookkeeping.
 *
 * <p>Label scheme: each loop's {@link Ctx} mints its Laurel break/continue
 * labels on demand, when {@code loopEdge} first emits an {@code Exit} to it.
 * The loop wraps in labeled blocks only if labels were minted — it wraps only
 * if a jump asked for it.
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
        final int header, exit, latch, continueTarget;
        String breakLabel;      // Laurel label, minted on demand
        String continueLabel;

        Ctx(boolean loop, int header, int exit, int latch, int continueTarget) {
            this.loop = loop;
            this.header = header;
            this.exit = exit;
            this.latch = latch;
            this.continueTarget = continueTarget;
        }
    }

    final Deque<Ctx> ctxStack = new ArrayDeque<>();
    final Set<Integer> emittingLoops = new HashSet<>();
    final Set<Integer> loopEntryPass = new HashSet<>();
    final Set<Integer> infiniteHeaders = new HashSet<>();
    final Map<Integer, List<Trap>> triesByStart = new HashMap<>();
    final Set<Integer> emittedTries = new HashSet<>();
    final Set<Integer> emittedSync = new HashSet<>();
    final Map<Integer, Integer> skipLead = new HashMap<>();
    final Map<Integer, Integer> skipTail = new HashMap<>();
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

            translateBlock(b, out);
            boolean viaGoto = b.gotoTarget >= 0;
            int next = viaGoto ? b.gotoTarget : b.fall;
            AstNode<StmtExpr> je = loopEdge(b.id, next, viaGoto);
            if (je != null) {
                out.add(je);
                return out;
            }
            cur = next;
        }
        return out;
    }

    // --- break/continue as Laurel Exit, labels minted on demand ---

    AstNode<StmtExpr> loopEdge(int from, int next, boolean viaGoto) {
        if (next < 0 || !viaGoto || ctxStack.isEmpty()) {
            return null;
        }
        Ctx top = ctxStack.peek();
        if (next == top.exit) {
            return exit(breakLabelOf(top));
        }
        boolean innermostLoop = true;
        for (Ctx c : ctxStack) {
            if (!c.loop) {
                continue;
            }
            if (next == c.continueTarget && from != c.latch) {
                return exit(continueLabelOf(c));
            }
            if (!innermostLoop && next == c.exit) {
                return exit(breakLabelOf(c));
            }
            innermostLoop = false;
        }
        return null;
    }

    /** Mint (once) and return the loop/switch's Laurel break label. */
    String breakLabelOf(Ctx c) {
        ensureLabels(c);
        return c.breakLabel;
    }

    String continueLabelOf(Ctx c) {
        ensureLabels(c);
        return c.continueLabel;
    }

    /** Both labels are minted together, as a pair (JFE's withLoopLabels discipline). */
    void ensureLabels(Ctx c) {
        if (c.breakLabel == null) {
            c.breakLabel = ctx.freshLabel("loop_break");
            c.continueLabel = ctx.freshLabel("loop_continue");
        }
    }

    AstNode<StmtExpr> exit(String label) {
        return body.e(new StmtExpr.Exit(label));
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
        // form still asserts the condition (parity with the driver, which
        // dropped the message via new StmtExpr.Assert(cond, empty)).
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

    // --- if / if-else ---

    int emitIf(int condBlk, Set<Integer> stop, List<AstNode<StmtExpr>> out) {
        BasicBlock c = cfg.blocks.get(condBlk);
        AstNode<StmtExpr> cond = conditionOf(c, out);
        // Refuse the CONDITION eagerly, before either branch is emitted (an
        // instanceof-on-opaque in the test beats a cast refusal inside the
        // then-branch) — JFE's refusal order, pinned by the corpus.
        body.checkPlaceable(cond);
        int thenE = c.fall, elseE = c.branch;
        int follow = dom.ipdom[condBlk];

        Set<Integer> sub = plus(stop, follow);
        if (elseE == follow || elseE < 0 || stop.contains(elseE)) {
            AstNode<StmtExpr> je = null;
            if (elseE >= 0 && inInfiniteLoop() && elseE == ctxStack.peek().exit) {
                je = exit(breakLabelOf(ctxStack.peek()));
            }
            if (je != null) {
                out.add(place(body.e(new StmtExpr.IfThenElse(LaurelTrees.negate(cond, ctx.method.unordered),
                        blockOf(List.of(je)), Optional.empty())), condBlk));
                return thenE;
            }
            List<AstNode<StmtExpr>> then = region(thenE, sub);
            out.add(place(body.e(new StmtExpr.IfThenElse(cond, blockOf(then), Optional.empty())), condBlk));
        } else if (thenE == follow || stop.contains(thenE)) {
            AstNode<StmtExpr> je = null;
            if (inInfiniteLoop() && thenE == ctxStack.peek().exit) {
                je = exit(breakLabelOf(ctxStack.peek()));
            }
            if (je != null) {
                out.add(place(body.e(new StmtExpr.IfThenElse(cond, blockOf(List.of(je)), Optional.empty())),
                        condBlk));
                return elseE;
            }
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

    boolean inInfiniteLoop() {
        return !ctxStack.isEmpty() && ctxStack.peek().loop && infiniteHeaders.contains(ctxStack.peek().header);
    }

    boolean jumpsStraightBackTo(int b, int target) {
        for (int i = 0, cur = b; i < 8 && cur >= 0 && cur < cfg.blocks.size(); i++) {
            if (cur == target) {
                return true;
            }
            BasicBlock k = cfg.blocks.get(cur);
            if (k.instrs.size() != 1 || k.gotoTarget < 0) {
                return false;
            }
            cur = k.gotoTarget;
        }
        return false;
    }

    // --- loops ---

    Span headerSpan(int header) {
        int seqIdx = terminatorOf(header);
        return seqIdx < 0 ? Span.NONE : body.spanAt(seqIdx);
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
                if (!armIsValue(b.fall, m, new HashSet<>()) || !armIsValue(b.branch, m, new HashSet<>())) {
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
        // binding in Laurel; the IsType/deferred marker refuses at placement
        // exactly as the driver refused an instanceof with a pattern.
        int mark = body.stmts.size();
        AstNode<StmtExpr> thenVal = evalArm(c.fall, merge);
        AstNode<StmtExpr> elseVal = evalArm(c.branch, merge);
        if (body.stmts.size() != mark) {
            throw new StructException("value arm produced statements");
        }
        body.stack.push(buildTernary(cond, thenVal, elseVal));
    }

    AstNode<StmtExpr> buildTernary(AstNode<StmtExpr> cond, AstNode<StmtExpr> thenVal, AstNode<StmtExpr> elseVal) {
        boolean boolish = isBoolish(thenVal) || isBoolish(elseVal);
        if (boolish) {
            thenVal = body.coerce(thenVal, "Z");
            elseVal = body.coerce(elseVal, "Z");
        }
        AstNode<StmtExpr> r;
        if (boolish && LaurelTrees.isFalseLit(elseVal.val())) {
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
        return r;
    }

    AstNode<StmtExpr> evalArm(int entry, int merge) {
        int cur = entry;
        while (cur != merge) {
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
                    body.stack.push(evalTernaryValue(cur, m));
                    cur = m;
                }
            } else {
                List<CodeElement> ins = elems(b);
                body.translateSpan(ins, elemIdxs(b), 0, ins.size());
                cur = b.gotoTarget >= 0 ? b.gotoTarget : b.fall;
            }
        }
        return body.stack.pop();
    }

    AstNode<StmtExpr> evalTernaryValue(int condBlk, int merge) {
        BasicBlock c = cfg.blocks.get(condBlk);
        visited[condBlk] = true;
        List<CodeElement> ins = elems(c);
        body.translateSpan(ins, elemIdxs(c), 0, ins.size() - 1);
        AstNode<StmtExpr> cond = body.materializeComparison(c.last.opcode());
        AstNode<StmtExpr> thenVal = evalArm(c.fall, merge);
        AstNode<StmtExpr> elseVal = evalArm(c.branch, merge);
        return buildTernary(cond, thenVal, elseVal);
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
        return body.e(new StmtExpr.Block(new ArrayList<>(stmts), Optional.empty()));
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
