package org.strata.java.bytecode.verifier.compiler;

import org.strata.java.bytecode.frontend.BasicBlock;
import org.strata.java.bytecode.frontend.ControlFlowGraph;
import org.strata.java.bytecode.frontend.DominanceFinder;
import org.strata.java.bytecode.frontend.Trap;
import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.CatchClause;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.GotoDestination;
import org.strata.jverify.laurel.HighType;
import org.strata.jverify.laurel.Operation;
import org.strata.jverify.laurel.ParameterOpt;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.Variable;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Emits a method body in bytecode order: one labelled Laurel block per basic block
 * that a jump lands on, holding the code up to the next such block, and one
 * {@code if c then { goto T }} per branch. Laurel's {@code RestructureGotos}
 * nests the blocks into structured control flow, so no loop, join or nesting is
 * recovered here. The operand stack crosses a label on Laurel's {@code Stack}.
 *
 * <p>An exception-table range becomes {@code { try { ... } catch ... } T}, which a
 * jump into the range's first block from outside enters through {@code T}. A
 * handler, typed or catch-all, is a block of its own that the catch arm jumps to
 * with the exception pushed, so javac's finally handler ({@code astore t; F;
 * aload t; athrow}) runs {@code F} and rethrows. A range javac extends over its own
 * handler's first block is cut at that block. javac's assert idiom
 * becomes an {@code assert}, a string switch dispatches on the string, and a
 * loop's leading contract markers become its header block's invariants
 * ({@link LoopEmitter}).
 */
final class BlockEmitter {

    final ControlFlowGraph cfg;
    final DominanceFinder dom;
    final BodyTranslator body;
    final TranslationContext ctx;

    private final int n;
    private final boolean[] reachable;
    private final boolean[] consumed;
    private final boolean[] target;
    private final boolean[] boolProducer;
    private final int[] depthIn;
    private final Map<Integer, List<AstNode<StmtExpr>>> edgeStack = new HashMap<>();
    private final Map<Integer, List<AstNode<StmtExpr>>> carried = new HashMap<>();
    private final Map<Integer, Integer> skipLead = new HashMap<>();
    private final List<Try> tries = new ArrayList<>();
    private final Map<String, Integer> headerLabels = new HashMap<>();
    private final Map<String, Set<Integer>> regionBlocks = new HashMap<>();
    private final Map<String, String> fallsInto = new HashMap<>();
    private int lastBlock = -1;
    private boolean falls;
    private List<AstNode<StmtExpr>> region;

    /** One exception-table range, its entries in table order, and the ranges nested in it. */
    private record Try(int start, int end, List<Trap> traps, String label, List<Try> inner) {
        boolean contains(int b) {
            return start <= b && b < end;
        }
    }

    BlockEmitter(ControlFlowGraph cfg, DominanceFinder dom, BodyTranslator body, TranslationContext ctx) {
        this.cfg = cfg;
        this.dom = dom;
        this.body = body;
        this.ctx = ctx;
        this.n = cfg.blocks.size();
        this.reachable = new boolean[n];
        this.consumed = new boolean[n];
        this.target = new boolean[n];
        this.boolProducer = new boolean[n];
        this.depthIn = new int[n];
        markPatternBindTests();
    }

    /** The method body as a Laurel statement list. */
    List<AstNode<StmtExpr>> emit() {
        body.lateDecls = new ArrayList<>();
        return emitBody();
    }

    /**
     * A contract lambda's body: every diamond folds, so nothing needs a label or a
     * hoisted local, and values stay expressions rather than spilling to the stack.
     */
    List<AstNode<StmtExpr>> emitValue() {
        boolean wasSpilling = body.spilling;
        body.spilling = false;
        body.lateDecls = new ArrayList<>();
        valueOnly = true;
        try {
            return emitBody();
        } finally {
            body.spilling = wasSpilling;
        }
    }

    private boolean valueOnly;

    private List<Trap> traps = List.of();

    private List<AstNode<StmtExpr>> emitBody() {
        traps = cfg.excs;
        reach();
        buildTries();
        markBooleanJoins();
        for (int b = 0; b < n; b++) {
            if (reachable[b] && !consumed[b]) {
                lastBlock = b;
            }
        }
        List<AstNode<StmtExpr>> out = new ArrayList<>();
        region = out;
        emitRange(0, n, topTries(), out);
        closeRegion(out);
        out.addAll(body.stmts);
        body.stmts.clear();
        return new LoopEmitter(this).attachInvariants(out, headerLabels);
    }

    private void reach() {
        Deque<Integer> q = new ArrayDeque<>();
        q.add(0);
        reachable[0] = true;
        while (!q.isEmpty()) {
            int x = q.poll();
            List<Integer> next = new ArrayList<>(cfg.blocks.get(x).succ);
            for (Trap t : traps) {
                if (t.startBlk() <= x && x < t.endBlkId()) {
                    next.add(t.handlerBlk());
                }
            }
            for (int s : next) {
                if (s >= 0 && s < n && !reachable[s]) {
                    reachable[s] = true;
                    q.add(s);
                }
            }
        }
        for (int b = 0; b < n; b++) {
            if (!reachable[b]) {
                continue;
            }
            BasicBlock blk = cfg.blocks.get(b);
            for (int s : blk.succ) {
                if (s != blk.fall || s != b + 1 || s == blk.branch) {
                    target[s] = true;
                }
            }
        }
        for (Trap t : traps) {
            target[t.handlerBlk()] = true;
        }
        for (int b = 0; b < n; b++) {
            if (dom.isLoopHeader(b)) {
                target[b] = true;
            }
        }
    }

    /**
     * javac's boolean value: every block that reaches a join pushes just {@code 1}
     * or {@code 0} (and jumps or falls into it). Those blocks push {@code true} and
     * {@code false} instead, so a merged boolean reads as one.
     */
    private void markBooleanJoins() {
        for (int j = 0; j < n; j++) {
            List<Integer> preds = cfg.blocks.get(j).preds;
            if (!reachable[j] || preds.stream().filter(p -> reachable[p]).count() < 2) {
                continue;
            }
            boolean all = true;
            for (int p : preds) {
                all &= !reachable[p] || pushesBooleanConstant(cfg.blocks.get(p), j);
            }
            if (all) {
                for (int p : preds) {
                    boolProducer[p] = reachable[p];
                }
            }
        }
    }

    private boolean pushesBooleanConstant(BasicBlock b, int join) {
        List<Integer> ins = b.instrs;
        boolean jumps = ins.size() == 2 && b.gotoTarget == join;
        boolean falls = ins.size() == 1 && b.fall == join && b.succ.size() == 1;
        if (!jumps && !falls) {
            return false;
        }
        return cfg.seq.get(ins.get(0)) instanceof ConstantInstruction ci
                && ci.constantValue() instanceof Integer v && (v == 0 || v == 1);
    }

    private void buildTries() {
        Map<String, Try> byRange = new java.util.LinkedHashMap<>();
        for (Trap t : traps) {
            int end = t.startBlk() <= t.handlerBlk() && t.handlerBlk() < t.endBlkId() ? t.handlerBlk() : t.endBlkId();
            if (t.startBlk() >= end || !reachable[t.startBlk()]) {
                continue;
            }
            byRange.computeIfAbsent(t.startBlk() + ":" + end,
                    k -> new Try(t.startBlk(), end, new ArrayList<>(), ctx.freshLabel("try"),
                            new ArrayList<>())).traps().add(t);
        }
        tries.addAll(byRange.values());
        for (Try a : tries) {
            for (Try b : tries) {
                boolean overlap = a.start < b.end && b.start < a.end;
                boolean nested = (a.start <= b.start && b.end <= a.end) || (b.start <= a.start && a.end <= b.end);
                if (overlap && !nested) {
                    throw new JavaViolationException("unsupported try shape: overlapping exception ranges");
                }
            }
        }
        tries.sort((a, b) -> a.start != b.start ? Integer.compare(a.start, b.start) : Integer.compare(b.end, a.end));
        for (Try t : tries) {
            Try parent = null;
            for (Try p : tries) {
                if (p != t && p.start <= t.start && t.end <= p.end
                        && (p.start != t.start || p.end != t.end)
                        && (parent == null || (parent.start <= p.start && p.end <= parent.end))) {
                    parent = p;
                }
            }
            if (parent != null) {
                parent.inner.add(t);
            }
            target[t.start] = true;
            if (t.end < n) {
                target[t.end] = true;
            }
        }
    }

    private List<Try> topTries() {
        List<Try> top = new ArrayList<>();
        for (Try t : tries) {
            if (tries.stream().noneMatch(p -> p.inner.contains(t))) {
                top.add(t);
            }
        }
        return top;
    }

    /** The block a label names, or null for a try wrapper (whose range starts at its first block). */
    Integer blockOfLabel(String label) {
        for (Try t : tries) {
            if (t.label.equals(label)) {
                return t.start;
            }
        }
        int at = label.lastIndexOf("bb");
        if (at < 0) {
            return null;
        }
        try {
            return Integer.parseInt(label.substring(at + 2));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The labelled block that the one labelled {@code label} falls into, if any. */
    String fallsInto(String label) {
        return fallsInto.get(label);
    }

    /** Every predecessor of the block labelled {@code label} lies in one of the blocks labelled {@code from}. */
    boolean enteredOnlyFrom(String label, List<String> from) {
        Integer b = blockOfLabel(label);
        if (b == null || b < 0 || b >= n) {
            return false;
        }
        Set<Integer> inside = new HashSet<>();
        for (String l : from) {
            inside.addAll(regionBlocks.getOrDefault(l, Set.of()));
        }
        return inside.containsAll(cfg.blocks.get(b).preds);
    }

    private String labelOf(int b) {
        return "bb" + b;
    }

    /** A try's wrapper label. */
    private String labelOf(Try t) {
        return t.label;
    }

    /** The jump from block {@code from} to block {@code to}: into a try range only through its wrapper. */
    private AstNode<StmtExpr> jump(int from, int to) {
        String l = labelOf(to);
        for (Try t : tries) {
            if (t.start == to && !t.contains(from)) {
                l = labelOf(t);
                break;
            }
        }
        return place(body.e(new StmtExpr.Goto(l, new GotoDestination.BlockStart())), from);
    }

    private void emitRange(int from, int to, List<Try> inner, List<AstNode<StmtExpr>> out) {
        emitRange(from, to, inner, out, false);
    }

    private void emitRange(int from, int to, List<Try> inner, List<AstNode<StmtExpr>> out, boolean tryBody) {
        List<AstNode<StmtExpr>> saved = region;
        region = out;
        boolean savedFalls = falls;
        int savedLoose = looseStart;
        looseStart = out.size();
        falls = true;
        int ti = 0;
        int b = from;
        while (b < to) {
            Try t = ti < inner.size() && inner.get(ti).start == b ? inner.get(ti++) : null;
            if (t != null) {
                closeRegion(out);
                sealLoose(out);
                if (falls) {
                    recordEdge(t.start);
                }
                out.add(emitTry(t));
                looseStart = out.size();
                b = t.end;
                falls = true;
                continue;
            }
            if (!reachable[b] || consumed[b]) {
                b++;
                continue;
            }
            if (target[b]) {
                if (falls) {
                    flush();
                    recordEdge(b);
                    if (openLabel != null) {
                        fallsInto.put(openLabel, labelOf(b));
                    }
                }
                closeRegion(out);
                sealLoose(out);
                openRegion(b);
            }
            if (openLabel != null) {
                regionBlocks.computeIfAbsent(openLabel, k -> new HashSet<>()).add(b);
            }
            b = emitBlock(b);
        }
        if (falls && to < n) {
            if (tryBody && !body.stack.isEmpty()) {
                carryOut(to);
            } else {
                flush();
                recordEdge(to);
            }
        }
        closeRegion(out);
        region = saved;
        falls = savedFalls;
        looseStart = savedLoose;
    }

    private int looseStart;

    /**
     * Enclose the statements emitted outside any labelled block since the last one,
     * from the first that declares a local, in a block of their own: the labelled
     * blocks after them would otherwise lie in its scope, which a jump to them may
     * not enter.
     */
    private void sealLoose(List<AstNode<StmtExpr>> out) {
        drain();
        int first = looseStart;
        while (first < out.size() && !declares(out.get(first))) {
            first++;
        }
        List<AstNode<StmtExpr>> loose = out.subList(first, out.size());
        if (!loose.isEmpty()) {
            var block = body.e(new StmtExpr.Block(new ArrayList<>(loose), Optional.empty(), List.of(), Optional.empty()));
            loose.clear();
            out.add(block);
        }
        looseStart = out.size();
    }

    private static boolean declares(AstNode<StmtExpr> s) {
        return switch (s.val()) {
            case StmtExpr.Assign a -> a.targets().stream().anyMatch(t -> t.val() instanceof Variable.Declare);
            case StmtExpr.Var v -> v.var_() instanceof Variable.Declare;
            default -> false;
        };
    }

    private String openLabel;
    private List<AstNode<StmtExpr>> openStmts;
    private int openBlock;

    private void openRegion(int b) {
        openLabel = labelOf(b);
        openStmts = new ArrayList<>();
        openBlock = b;
        if (dom.isLoopHeader(b)) {
            headerLabels.put(openLabel, b);
        }
        body.stack.clear();
        if (carried.containsKey(b)) {
            for (AstNode<StmtExpr> v : carried.get(b)) {
                body.stack.push(body.e(v.val()));
                body.copyFacts(v, body.stack.peek());
            }
            return;
        }
        List<AstNode<StmtExpr>> incoming = edgeStack.get(b);
        for (int i = 0; i < depthIn[b]; i++) {
            AstNode<StmtExpr> read = BodyTranslator.stackRead(SourceMap.UNKNOWN_RANGE);
            if (incoming != null && i < incoming.size()) {
                body.copyFacts(incoming.get(i), read);
            }
            body.stack.push(read);
        }
    }

    private void closeRegion(List<AstNode<StmtExpr>> out) {
        drain();
        if (openLabel != null) {
            out.add(place(body.e(new StmtExpr.Block(openStmts, Optional.of(openLabel), List.of(),
                    Optional.empty())), openBlock));
            openLabel = null;
            openStmts = null;
            looseStart = out.size();
        }
    }

    private List<AstNode<StmtExpr>> cur() {
        return openStmts != null ? openStmts : region;
    }

    private void drain() {
        cur().addAll(body.stmts);
        body.stmts.clear();
    }

    private void add(AstNode<StmtExpr> s) {
        drain();
        cur().add(s);
    }

    /**
     * Put every operand-stack value still held on the translator's side onto
     * Laurel's stack, where a jump carries it: the held values are pushed above the
     * ones already there, and a permutation restores the stack's order.
     */
    private void flush() {
        List<AstNode<StmtExpr>> entries = new ArrayList<>(body.stack);
        java.util.Collections.reverse(entries);
        if (entries.stream().allMatch(BodyTranslator::isStackRead)) {
            return;
        }
        int reads = (int) entries.stream().filter(BodyTranslator::isStackRead).count();
        int[] order = new int[entries.size()];
        int r = 0;
        int h = reads;
        boolean identity = true;
        for (int p = 0; p < entries.size(); p++) {
            order[p] = BodyTranslator.isStackRead(entries.get(p)) ? r++ : h++;
            identity &= order[p] == p;
        }
        List<AstNode<StmtExpr>> pushed = new ArrayList<>();
        for (AstNode<StmtExpr> e : entries) {
            pushed.add(BodyTranslator.isStackRead(e) ? e : body.pushValue(e));
        }
        if (!identity) {
            body.emitPermute(entries.size(), order, entries.get(0).source());
        }
        body.stack.clear();
        for (int p = 0; p < entries.size(); p++) {
            AstNode<StmtExpr> read = BodyTranslator.stackRead(entries.get(p).source());
            body.copyFacts(pushed.get(p), read);
            body.stack.push(read);
        }
        drain();
    }

    /**
     * Store the values a try body leaves on the operand stack -- javac ends the
     * protected range before the instruction that consumes them, as in
     * {@code try { return f(); }} -- in locals the block after the try reads them
     * from: the stack does not cross a try boundary.
     */
    private void carryOut(int to) {
        List<AstNode<StmtExpr>> entries = new ArrayList<>(body.stack);
        java.util.Collections.reverse(entries);
        body.stack.clear();
        List<AstNode<StmtExpr>> reads = new ArrayList<>();
        for (AstNode<StmtExpr> v : entries) {
            TypeRef t = body.staticTypeOf.get(v);
            if (t == null && body.descOf.get(v) != null) {
                t = TypeRef.ofDescriptor(body.descOf.get(v), false, false);
            }
            if (t == null) {
                throw new JavaViolationException("unsupported try shape: a value leaves the try on the operand stack");
            }
            String name = ctx.freshLabel("java?carry");
            body.lateDecls.add(body.e(new StmtExpr.Var(new Variable.Declare(new ParameterOpt(LaurelFactory.ident(name),
                    Optional.of(LaurelFactory.node(t.toLaurel(ctx.typeParams, ctx.methodTypeParams, ctx.resolver,
                            ctx.composites))))))));
            add(body.e(new StmtExpr.Assign(List.of(new AstNode<>(new Variable.Local(LaurelFactory.ident(name)),
                    SourceMap.UNKNOWN_RANGE)), v)));
            AstNode<StmtExpr> read = body.e(LaurelFactory.var_(name));
            body.copyFacts(v, read);
            reads.add(read);
        }
        carried.put(to, reads);
    }

    private void recordEdge(int t) {
        if (t < 0 || t >= n) {
            return;
        }
        if (!edgeStack.containsKey(t)) {
            List<AstNode<StmtExpr>> entries = new ArrayList<>(body.stack);
            java.util.Collections.reverse(entries);
            edgeStack.put(t, entries);
            depthIn[t] = entries.size();
        }
    }

    /** Emit block {@code b}; the next block to emit. Sets {@link #falls}. */
    private int emitBlock(int b) {
        BasicBlock blk = cfg.blocks.get(b);
        if (blk.last != null && blk.last.opcode() == Opcode.MONITORENTER) {
            throw new JavaViolationException("Unsupported statement: SynchronizedStmt");
        }
        Integer after = tryAssert(b);
        if (after != null) {
            return fallTo(b, after);
        }
        if (isStringSwitch(blk)) {
            emitStringSwitch(b);
            falls = false;
            return b + 1;
        }
        int lead = skipLead.getOrDefault(b, 0);
        List<Integer> idxs = blk.instrs.subList(lead, blk.instrs.size());
        Instruction last = idxs.isEmpty() ? null : (Instruction) cfg.seq.get(idxs.get(idxs.size() - 1));
        boolean structural = last != null
                && (last instanceof BranchInstruction || blk.isSwitch());
        int body0 = structural ? idxs.size() - 1 : idxs.size();
        translate(idxs.subList(0, body0));
        if (boolProducer[b] && !body.stack.isEmpty()) {
            AstNode<StmtExpr> v = body.coerce(body.stack.pop(), "Z");
            body.bools.add(v);
            body.stack.push(v);
        }
        if (!structural) {
            if (blk.succ.isEmpty()) {
                if (last != null && last.opcode() == Opcode.RETURN && b != lastBlock) {
                    add(body.e(new StmtExpr.Return(List.of())));
                }
                drain();
                falls = false;
                return b + 1;
            }
            return fallTo(b, blk.fall);
        }
        body.pos = idxs.get(idxs.size() - 1);
        if (blk.isSwitch()) {
            Integer folded = fold(b, dom.ipdom[b], 1);
            if (folded != null) {
                return folded;
            }
            emitSwitch(b, blk);
            falls = false;
            return b + 1;
        }
        if (blk.gotoTarget >= 0) {
            return fallTo(b, blk.gotoTarget);
        }
        BranchInstruction br = (BranchInstruction) last;
        int k = idxs.get(idxs.size() - 1);
        Integer folded = fold(b, dom.ipdom[b], operands(br));
        if (folded != null) {
            return folded;
        }
        body.pos = k;
        AstNode<StmtExpr> cond = body.materializeComparison(br.opcode());
        body.checkPlaceable(cond);
        for (AstNode<StmtExpr> t : body.explicitImplicitThrows(cond)) {
            add(place(t, b));
        }
        if (readsStack(cond) && !body.stack.stream().allMatch(BodyTranslator::isStackRead)) {
            cond = body.e(LaurelFactory.var_(bindSelector(cond, new HighType.TBool())));
        }
        flush();
        recordEdge(blk.branch);
        AstNode<StmtExpr> guard = place(body.e(new StmtExpr.IfThenElse(LaurelTrees.negate(cond),
                blockOf(List.of(jump(b, blk.branch))), Optional.empty())), b);
        body.stmtSeq.put(guard, k);
        add(guard);
        return fallTo(b, blk.fall);
    }

    /**
     * Fold the value the branch ending block {@code b} computes up to {@code join}
     * ({@link ValueFolder}) and carry on in {@code join}; null, with nothing emitted,
     * when the region is not a pure value.
     */
    private Integer fold(int b, int join, int operands) {
        if (!pureRegion(b, join)) {
            return null;
        }
        int depth = body.stack.size();
        int mark = body.stmts.size();
        var saved = new ArrayList<>(body.stack);
        boolean wasSpilling = body.spilling;
        body.spilling = false;
        AstNode<StmtExpr> v = null;
        try {
            v = new ValueFolder(body, cfg, dom.ipdom, join).branch(b);
        } catch (ValueFolder.NotAValue e) {
            v = null;
        } finally {
            body.spilling = wasSpilling;
        }
        if (v != null && body.stmts.size() == mark && body.stack.size() == depth - operands) {
            body.stack.push(v);
            body.settle();
            for (int x = b + 1; x < join; x++) {
                consumed[x] = true;
            }
            consumed[join] = true;
            return emitBlock(join);
        }
        body.stmts.subList(mark, body.stmts.size()).clear();
        body.stack.clear();
        body.stack.addAll(saved);
        return null;
    }

    /**
     * The blocks strictly between {@code b} and its immediate post-dominator
     * {@code join} form a single-entry region that only computes values: every
     * block is entered from inside it, and no instruction stores, writes or throws.
     */
    private boolean pureRegion(int b, int join) {
        if (join <= b + 1 || join >= n || (!valueOnly && copiesBlocks(b, join))) {
            return false;
        }
        for (int x = b + 1; x <= join; x++) {
            if (!reachable[x] || consumed[x] || dom.isLoopHeader(x) || skipLead.containsKey(x)
                    || !dom.dominates(b, x)) {
                return false;
            }
            for (int p : cfg.blocks.get(x).preds) {
                if (p < b || p >= join) {
                    return false;
                }
            }
            final int blk = x;
            if (tries.stream().anyMatch(t -> t.start == blk || t.end == blk)
                    || traps.stream().anyMatch(e -> e.handlerBlk() == blk)) {
                return false;
            }
            if (x == join) {
                break;
            }
            for (int i : cfg.blocks.get(x).instrs) {
                CodeElement el = cfg.seq.get(i);
                boolean ok = el instanceof ConstantInstruction || el instanceof LoadInstruction
                        || el instanceof java.lang.classfile.instruction.OperatorInstruction
                        || el instanceof java.lang.classfile.instruction.ConvertInstruction
                        || (el instanceof FieldInstruction f
                            && (f.opcode() == Opcode.GETFIELD || f.opcode() == Opcode.GETSTATIC))
                        || el instanceof InvokeInstruction
                        || el instanceof java.lang.classfile.instruction.InvokeDynamicInstruction
                        || el instanceof java.lang.classfile.instruction.ArrayLoadInstruction
                        || el instanceof TypeCheckInstruction
                        || el instanceof BranchInstruction
                        || el instanceof java.lang.classfile.instruction.TableSwitchInstruction
                        || el instanceof java.lang.classfile.instruction.LookupSwitchInstruction;
                if (!ok) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Folding the region from {@code b} to {@code join} would translate one of its
     * blocks more than once: a block entered from two places in the region is copied
     * into each, unless it is the join of a nested conditional, which is folded there
     * first, or only pushes a constant or a local. Such a region (the rest of a
     * short-circuit chain) is emitted with jumps instead, once.
     */
    private boolean copiesBlocks(int b, int join) {
        Set<Integer> nestedJoins = new HashSet<>();
        for (int x = b; x < join; x++) {
            int j = dom.ipdom[x];
            if (j > x && j < join && cfg.blocks.get(x).isConditional()) {
                nestedJoins.add(j);
            }
        }
        for (int x = b + 1; x < join; x++) {
            if (cfg.blocks.get(x).preds.size() > 1 && !nestedJoins.contains(x) && !isLeaf(x, join)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLeaf(int x, int join) {
        BasicBlock blk = cfg.blocks.get(x);
        if (blk.isConditional() || blk.isSwitch() || (blk.gotoTarget >= 0 ? blk.gotoTarget : blk.fall) != join) {
            return false;
        }
        for (int i : blk.instrs) {
            CodeElement el = cfg.seq.get(i);
            if (!(el instanceof ConstantInstruction || el instanceof LoadInstruction || el instanceof BranchInstruction)) {
                return false;
            }
        }
        return true;
    }

    private static int operands(BranchInstruction br) {
        return switch (br.opcode()) {
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE -> 2;
            default -> 1;
        };
    }

    private int fallTo(int b, int t) {
        if (t == b + 1 && t < n) {
            falls = true;
            return b + 1;
        }
        flush();
        recordEdge(t);
        add(jump(b, t));
        falls = false;
        return b + 1;
    }

    private void translate(List<Integer> idxs) {
        List<CodeElement> ins = new ArrayList<>();
        for (int i : idxs) {
            ins.add(cfg.seq.get(i));
        }
        body.translateSpan(ins, idxs, 0, ins.size());
        drain();
    }

    private void emitSwitch(int b, BasicBlock blk) {
        AstNode<StmtExpr> key = body.stack.pop();
        String sel = bindSelector(key, new HighType.UserDefined(LaurelFactory.ident("int32")));
        flush();
        for (int[] c : blk.cases) {
            dispatch(b, body.e(body.pinnedLongLiteral(c[0])), sel, c[1]);
        }
        recordEdge(blk.defaultCase);
        add(jump(b, blk.defaultCase));
    }

    /**
     * A fresh local holding {@code v}, bound before the held stack entries are
     * pushed: a value that reads the operand stack must read it before they land on top.
     */
    private String bindSelector(AstNode<StmtExpr> v, HighType type) {
        String sel = "java?sel" + (ctx.switchSelCounter++);
        add(body.e(new StmtExpr.Assign(List.of(new AstNode<>(new Variable.Declare(new ParameterOpt(
                LaurelFactory.ident(sel), Optional.of(LaurelFactory.node(type)))), SourceMap.UNKNOWN_RANGE)),
                v)));
        return sel;
    }

    private static boolean readsStack(AstNode<StmtExpr> v) {
        return BodyTranslator.isStackRead(v) || BodyTranslator.childrenOf(v.val()).stream().anyMatch(BlockEmitter::readsStack);
    }

    private void dispatch(int b, AstNode<StmtExpr> value, String sel, int to) {
        recordEdge(to);
        AstNode<StmtExpr> test = body.e(LaurelFactory.primitiveOp(new Operation.Eq(),
                body.e(LaurelFactory.var_(sel)), value));
        add(place(body.e(new StmtExpr.IfThenElse(test, blockOf(List.of(jump(b, to))), Optional.empty())), b));
    }

    /** javac's string switch: a {@code hashCode} switch whose arms store a case index, then a switch on it. */
    private boolean isStringSwitch(BasicBlock b) {
        if (!b.isSwitch() || b.instrs.size() < 2 || b.defaultCase < 0) {
            return false;
        }
        Instruction sel = (Instruction) cfg.seq.get(b.instrs.get(b.instrs.size() - 2));
        return sel instanceof InvokeInstruction inv && inv.name().stringValue().equals("hashCode")
                && inv.typeSymbol().returnType().descriptorString().equals("I")
                && cfg.blocks.get(b.defaultCase).isSwitch();
    }

    private void emitStringSwitch(int p1) {
        BasicBlock b = cfg.blocks.get(p1);
        translate(b.instrs.subList(0, b.instrs.size() - 2));
        AstNode<StmtExpr> selector = body.stack.isEmpty()
                ? body.e(new StmtExpr.LiteralString("?")) : body.stack.pop();
        flush();
        int p2 = b.defaultCase;
        Map<Integer, List<String>> idxToStrs = new HashMap<>();
        String lastStr = null;
        Integer lastInt = null;
        for (int id = p1 + 1; id < p2; id++) {
            consumed[id] = true;
            for (int si : cfg.blocks.get(id).instrs) {
                CodeElement e = cfg.seq.get(si);
                if (e instanceof ConstantInstruction ci) {
                    if (ci.constantValue() instanceof String s) {
                        lastStr = s;
                    } else if (ci.constantValue() instanceof Integer iv) {
                        lastInt = iv;
                    }
                } else if (e instanceof StoreInstruction) {
                    if (lastStr != null && lastInt != null) {
                        idxToStrs.computeIfAbsent(lastInt, k -> new ArrayList<>()).add(lastStr);
                    }
                    lastStr = null;
                    lastInt = null;
                }
            }
        }
        consumed[p2] = true;
        String sel = "java?sel" + (ctx.switchSelCounter++);
        add(body.e(new StmtExpr.Assign(List.of(new AstNode<>(new Variable.Declare(new ParameterOpt(
                LaurelFactory.ident(sel), Optional.empty())), SourceMap.UNKNOWN_RANGE)), selector)));
        BasicBlock sw = cfg.blocks.get(p2);
        for (int[] c : sw.cases) {
            for (String s : idxToStrs.getOrDefault(c[0], List.of())) {
                dispatch(p1, body.e(new StmtExpr.LiteralString(s)), sel, c[1]);
            }
        }
        recordEdge(sw.defaultCase);
        add(jump(p1, sw.defaultCase));
    }

    private AstNode<StmtExpr> emitTry(Try t) {
        List<String> armTypes = new ArrayList<>();
        for (Trap e : t.traps) {
            armTypes.add(caughtFqn(e));
        }
        List<AstNode<StmtExpr>> tryBody = new ArrayList<>();
        ctx.method.enclosingCatchTypes.push(armTypes);
        try {
            emitRange(t.start, t.end, t.inner, tryBody, true);
        } finally {
            ctx.method.enclosingCatchTypes.pop();
        }
        List<CatchClause> clauses = new ArrayList<>();
        int i = 0;
        while (i < t.traps.size()) {
            int h = t.traps.get(i).handlerBlk();
            List<String> caught = new ArrayList<>();
            while (i < t.traps.size() && t.traps.get(i).handlerBlk() == h) {
                caught.add(caughtFqn(t.traps.get(i)));
                i++;
            }
            clauses.add(catchClause(t, h, caught));
        }
        AstNode<StmtExpr> tr = body.e(new StmtExpr.Try(blockOf(tryBody), clauses, Optional.empty()));
        return place(body.e(new StmtExpr.Block(List.of(tr), Optional.of(labelOf(t)), List.of(), Optional.empty())),
                t.start);
    }

    private String caughtFqn(Trap e) {
        return e.type() == null ? ctx.resolver.resolveClassFqn("Throwable")
                : body.resolveFqn(TypeRef.ofClassDesc(e.type()));
    }

    private CatchClause catchClause(Try t, int h, List<String> caught) {
        String binding = ctx.freshLabel("exc");
        StmtExpr predicate = null;
        for (String fqn : caught) {
            ctx.composites.registerHierarchyUpToThrowable(fqn);
            StmtExpr test = new StmtExpr.IsType(body.e(LaurelFactory.var_(binding)),
                    LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(fqn))));
            predicate = predicate == null ? test
                    : LaurelFactory.primitiveOp(new Operation.Or(), body.e(predicate), body.e(test));
        }
        List<AstNode<StmtExpr>> arm = new ArrayList<>();
        boolean catchAll = t.traps.stream().anyMatch(e -> e.handlerBlk() == h && e.type() == null);
        AstNode<StmtExpr> value = body.e(LaurelFactory.var_(binding));
        String armType = catchAll ? null : armTypeOf(caught);
        if (armType != null) {
            value = body.e(new StmtExpr.AsType(value,
                    LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(armType)))));
        }
        BasicBlock hb = cfg.blocks.get(h);
        CodeElement first = hb.instrs.isEmpty() ? null : cfg.seq.get(hb.instrs.get(0));
        body.stack.clear();
        body.stack.push(value);
        if (catchAll && first instanceof StoreInstruction si) {
            int at = hb.instrs.get(0);
            body.pos = cfg.nextInstr[at + 1] >= 0 ? cfg.nextInstr[at + 1] : at;
            body.caughtAllSlots.add(body.varKey(si.slot()));
            body.stack.pop();
            body.stack.push(body.pushValue(value));
            recordEdge(h);
        } else if (first instanceof StoreInstruction si) {
            int at = hb.instrs.get(0);
            body.pos = cfg.nextInstr[at + 1] >= 0 ? cfg.nextInstr[at + 1] : at;
            body.store(si);
            skipLead.put(h, 1);
        } else if (first instanceof java.lang.classfile.instruction.StackInstruction sk
                && sk.opcode() == Opcode.POP) {
            body.stack.pop();
            skipLead.put(h, 1);
        } else {
            body.stack.pop();
            body.stack.push(body.pushValue(value));
            recordEdge(h);
        }
        arm.addAll(body.stmts);
        body.stmts.clear();
        arm.add(jump(t.start, h));
        body.stack.clear();
        return new CatchClause(LaurelFactory.ident(binding), Optional.of(body.e(predicate)), blockOf(arm),
                LaurelFactory.node(catchAll ? new HighType.UserDefined(LaurelFactory.ident(caught.getFirst()))
                        : new HighType.Unknown()));
    }

    private String armTypeOf(List<String> caught) {
        String arm = caught.getFirst();
        for (String fqn : caught) {
            arm = arm == null ? null : ctx.resolver.commonSuperclass(fqn, arm);
        }
        return arm;
    }

    private Integer tryAssert(int guardBlk) {
        BasicBlock g = cfg.blocks.get(guardBlk);
        if (!g.isConditional() || g.instrs.size() < 2) {
            return null;
        }
        int k = g.instrs.size();
        if (!(insAt(g, k - 2) instanceof FieldInstruction fi) || fi.opcode() != Opcode.GETSTATIC
                || !fi.name().stringValue().equals("$assertionsDisabled") || g.last.opcode() != Opcode.IFNE) {
            return null;
        }
        int after = g.branch;
        int condBlk = g.fall;
        if (after < 0 || condBlk < 0 || condBlk >= n || cfg.blocks.get(condBlk).preds.size() != 1) {
            return null;
        }
        BasicBlock c = cfg.blocks.get(condBlk);
        if (!c.isConditional() || c.branch != after || c.fall < 0 || c.fall >= n) {
            return null;
        }
        int throwBlk = c.fall;
        BasicBlock t = cfg.blocks.get(throwBlk);
        if (t.last == null || t.last.opcode() != Opcode.ATHROW || !throwsAssertionError(t)
                || t.preds.size() != 1) {
            return null;
        }
        consumed[condBlk] = true;
        consumed[throwBlk] = true;
        translate(g.instrs.subList(0, k - 2));
        translate(c.instrs.subList(0, c.instrs.size() - 1));
        body.pos = c.instrs.get(c.instrs.size() - 1);
        AstNode<StmtExpr> cond = LaurelTrees.negate(body.materializeComparison(c.last.opcode()));
        add(place(body.e(new StmtExpr.Assert(cond, Optional.empty())), condBlk));
        return after;
    }

    private boolean throwsAssertionError(BasicBlock t) {
        for (int idx : t.instrs) {
            if (cfg.seq.get(idx) instanceof InvokeInstruction inv && inv.name().stringValue().equals("<init>")
                    && inv.owner().asInternalName().equals("java/lang/AssertionError")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Record every {@code INSTANCEOF} that tests a pattern-bind shape, so
     * {@link BodyTranslator#typeCheck} refuses an {@code instanceof} with a
     * pattern: Laurel's {@code is} has nowhere to put the binding. The
     * instructions alone do not identify a pattern -- a hand-written cast after an
     * {@code instanceof} compiles identically -- but where the source statements
     * start does: a manual cast is its own statement, so a CRT range or a
     * LineNumberTable entry begins at its LOAD, while a pattern's binding lives
     * inside the {@code if} condition. With neither table the binding is treated
     * as a pattern, the side that cannot mistranslate.
     */
    private void markPatternBindTests() {
        for (BasicBlock c : cfg.blocks) {
            if (!c.isConditional() || c.last.opcode() != Opcode.IFEQ) {
                continue;
            }
            int k = c.instrs.size();
            if (k < 2 || !(insAt(c, k - 2) instanceof TypeCheckInstruction ti) || ti.opcode() != Opcode.INSTANCEOF) {
                continue;
            }
            int fb = c.fall;
            if (fb < 0 || fb >= n) {
                continue;
            }
            BasicBlock b = cfg.blocks.get(fb);
            if (b.instrs.size() < 4 || !(insAt(b, 0) instanceof LoadInstruction)
                    || !(insAt(b, 1) instanceof TypeCheckInstruction cc) || cc.opcode() != Opcode.CHECKCAST
                    || !(insAt(b, 2) instanceof StoreInstruction)) {
                continue;
            }
            if (!startsOwnStatement(b.instrs.get(0))) {
                body.patternBindTests.add(ti);
            }
        }
    }

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

    private Instruction insAt(BasicBlock b, int k) {
        return (Instruction) cfg.seq.get(b.instrs.get(k));
    }

    AstNode<StmtExpr> blockOf(List<AstNode<StmtExpr>> stmts) {
        return body.e(new StmtExpr.Block(new ArrayList<>(stmts), Optional.empty(), List.of(), Optional.empty()));
    }

    /** Stamp a structural statement with its controlling block's terminator span. */
    AstNode<StmtExpr> place(AstNode<StmtExpr> raw, int blkId) {
        int seqIdx = terminatorOf(blkId);
        FileRange range = seqIdx < 0 ? ctx.src.defaultRange : body.stmtRange(seqIdx, false);
        return body.place(raw, range);
    }

    int terminatorOf(int blkId) {
        if (blkId < 0 || blkId >= n) {
            return -1;
        }
        BasicBlock b = cfg.blocks.get(blkId);
        return b.instrs.isEmpty() ? -1 : b.instrs.get(b.instrs.size() - 1);
    }
}
