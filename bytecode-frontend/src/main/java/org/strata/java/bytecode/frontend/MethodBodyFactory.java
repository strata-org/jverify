package org.strata.java.bytecode.frontend;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.CharacterRangeInfo;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LocalVariableType;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds the per-method {@link MethodBody} the direct bytecode-to-Laurel
 * translator consumes: the control-flow graph ({@link #buildCfg}), dominators
 * and natural loops ({@link #analyze}), CharacterRangeTable source spans
 * ({@link #crtRanges}, {@link #spanOf}), and the hoisted-declaration analysis
 * — composed per method by {@link #bodyOf}.
 */
public final class MethodBodyFactory {

    private MethodBodyFactory() {
    }

    /**
     * The innermost source statement span covering the instruction at {@code seqIdx}, or {@link Span#NONE}
     * when the class carries no CRT. Narrowest pc window wins; ties break on the narrower source extent.
     */
    public static Span spanOf(List<CrtRange> crt, int[] seqToBci, int seqIdx) {
        if (crt.isEmpty() || seqToBci == null || seqIdx < 0 || seqIdx >= seqToBci.length) return Span.NONE;
        int bci = seqToBci[seqIdx];
        CrtRange best = null;
        for (CrtRange r : crt) {
            if (!r.covers(bci)) continue;
            if (best == null || r.pcWidth() < best.pcWidth()
                    || (r.pcWidth() == best.pcWidth() && r.narrowerSrcThan(best))) best = r;
        }
        return best == null ? Span.NONE : best.span();
    }

    /** The statement-flagged CRT ranges of a method (empty when the class was compiled without -Xjcov). */
    public static List<CrtRange> crtRanges(CodeAttribute code) {
        List<CrtRange> out = new ArrayList<>();
        code.findAttribute(Attributes.characterRangeTable()).ifPresent(crt -> {
            for (CharacterRangeInfo e : crt.characterRangeTable())
                if ((e.flags() & 0x0001) != 0)                      // CRT_STATEMENT
                    out.add(new CrtRange(e.startPc(), e.endPc(), toSpan(e)));
        });
        return out;
    }

    /**
     * The CRT ranges flagged STATEMENT or BLOCK. The outermost BLOCK range
     * opens at the method's declaration line (its {@code {}), which is where
     * the source frontend anchors method-level diagnostics -- the min start
     * over these is the method-name line, unlike the statement-only min (the
     * first body statement). Empty without {@code -Xjcov}.
     */
    public static List<CrtRange> crtEnvelopeRanges(CodeAttribute code) {
        List<CrtRange> out = new ArrayList<>();
        code.findAttribute(Attributes.characterRangeTable()).ifPresent(crt -> {
            for (CharacterRangeInfo e : crt.characterRangeTable())
                if ((e.flags() & 0x0003) != 0)                      // CRT_STATEMENT | CRT_BLOCK
                    out.add(new CrtRange(e.startPc(), e.endPc(), toSpan(e)));
        });
        return out;
    }

    /**
     * The best bytecode approximation of the class declaration line: the
     * minimum first line-number entry across ALL methods (constructors
     * included), or 0 when the class carries no line info.
     *
     * <p>For a compiler-SYNTHESIZED default constructor, javac attributes the
     * synthesized {@code super(...)} preamble (pc 0) to the class declaration
     * line, so that minimum IS the class's own line. For an EXPLICIT
     * constructor, pc 0 points at the constructor's signature line instead --
     * the class keyword's line does not survive into the classfile at all --
     * so the minimum over every method is the earliest member, an upper bound
     * that is never later than any single constructor's line. Exactness for
     * explicit-constructor classes is unrecoverable from bytecode.
     */
    public static int classDeclLine(ClassModel cm) {
        int best = 0;
        for (MethodModel m : cm.methods()) {
            CodeAttribute code = (CodeAttribute) m.findAttribute(Attributes.code()).orElse(null);
            if (code == null) {
                continue;
            }
            LineNumbers lines = LineNumbers.of(code);
            if (lines == null) {
                continue;
            }
            int line = lines.lineAt(0);
            if (line <= 0) {
                continue;
            }
            best = best == 0 ? line : Math.min(best, line);
        }
        return best;
    }

    /**
     * End pc of the CRT {@code block} range covering a catch clause whose handler begins at {@code handlerPc},
     * or -1 when there is no CRT.
     *
     * <p>This is how a catch clause's extent is recovered. The exception table records where a handler BEGINS
     * but never where it ends, so the handler and the code following the whole try statement fall into one
     * basic block. javac does record the clause's source extent as a block-flagged CRT range.
     *
     * <p>The range starts just AFTER the handler entry, not at it: the handler's first instruction is the
     * {@code astore} binding the exception, and the clause's source block opens at the statement after it.
     * Hence the small forward window rather than an exact match.
     */
    static int crtBlockEnd(CodeAttribute code, int handlerPc) {
        var attr = code.findAttribute(Attributes.characterRangeTable()).orElse(null);
        if (attr == null) return -1;
        int bestStart = Integer.MAX_VALUE, best = -1;
        for (CharacterRangeInfo e : attr.characterRangeTable()) {
            if ((e.flags() & 0x0002) == 0) continue;                  // CRT_BLOCK
            if (e.startPc() < handlerPc || e.startPc() > handlerPc + 3) continue;
            // closest block to the handler entry; among those the widest, since the clause encloses any
            // nested block that happens to open at the same pc
            if (e.startPc() < bestStart || (e.startPc() == bestStart && e.endPc() > best)) {
                bestStart = e.startPc(); best = e.endPc();
            }
        }
        return best;
    }

    /**
     * Bytecode pc of every element of {@code seq} (instructions + label targets), by accumulating
     * instruction sizes. A label target takes the pc of the instruction that follows it. This is the
     * bridge from the CFG's seq-indices to the pcs the CRT is keyed by.
     */
    public static int[] bciTable(List<CodeElement> seq) {
        int[] bci = new int[seq.size()];
        int pc = 0;
        for (int i = 0; i < seq.size(); i++) {
            bci[i] = pc;
            if (seq.get(i) instanceof Instruction in) pc += in.sizeInBytes();
        }
        return bci;
    }

    static Span toSpan(CharacterRangeInfo e) {
        int s = e.characterRangeStart(), t = e.characterRangeEnd();
        return new Span(s >>> 10, s & 1023, t >>> 10, t & 1023);
    }

    public static ControlFlowGraph buildCfg(ClassModel cm, MethodModel m) {
        CodeAttribute code = (CodeAttribute) m.findAttribute(Attributes.code()).orElseThrow();
        List<CodeElement> seq = new ArrayList<>();
        for (CodeElement e : code) if (e instanceof Instruction || e instanceof LabelTarget) seq.add(e);
        int n = seq.size();
        Map<Label,Integer> labelPos = new IdentityHashMap<>();
        for (int i = 0; i < n; i++) if (seq.get(i) instanceof LabelTarget lt) labelPos.put(lt.label(), i);
        int[] nextInstr = new int[n + 1]; nextInstr[n] = -1;
        for (int i = n - 1; i >= 0; i--) nextInstr[i] = (seq.get(i) instanceof Instruction) ? i : nextInstr[i + 1];
        List<Integer> instrIdx = new ArrayList<>();
        for (int i = 0; i < n; i++) if (seq.get(i) instanceof Instruction) instrIdx.add(i);

        List<ExceptionCatch> handlers = code.exceptionHandlers();

        // leaders: first instr; every branch/switch target; the instr after any terminator;
        // and every exception-table boundary (try start, try end, handler entry).
        Set<Integer> leaders = new java.util.TreeSet<>();
        if (!instrIdx.isEmpty()) leaders.add(instrIdx.get(0));
        for (int k = 0; k < instrIdx.size(); k++) {
            Instruction in = (Instruction) seq.get(instrIdx.get(k));
            if (isTerminator(in)) {
                for (int t : branchTargets(in, labelPos, nextInstr)) if (t >= 0) leaders.add(t);
                if (k + 1 < instrIdx.size()) leaders.add(instrIdx.get(k + 1));
            }
        }
        for (ExceptionCatch h : handlers) {
            int s = tgt(h.tryStart(), labelPos, nextInstr);   if (s >= 0) leaders.add(s);
            int e = tgt(h.tryEnd(),   labelPos, nextInstr);   if (e >= 0) leaders.add(e);
            int hd = tgt(h.handler(), labelPos, nextInstr);   if (hd >= 0) leaders.add(hd);
        }
        // The END of a catch block is recorded nowhere in the exception table, so the handler and the code
        // following the whole try statement fall into one basic block -- and that code then gets emitted
        // inside the catch, out of scope for anything after it.
        //
        // The CRT pins the boundary exactly: javac records the clause's source extent as a block-flagged
        // range whose start pc is the handler entry. Where there is no CRT (no -Xjcov) this falls back to the
        // catch PARAMETER's liveness scope, which usually coincides with the body but not always -- the
        // parameter can die at its last read while the body continues.
        // Only for a TYPED catch. A catch-all handler is the finally desugaring, whose folding assumes the
        // handler is a single block (`astore v; <finally>; aload v; athrow`); splitting it there breaks the
        // extraction of the finally body and the length of its inlined normal-path copy.
        int[] bci = bciTable(seq);
        for (ExceptionCatch h : handlers) {
            if (h.catchType().isEmpty()) continue;
            int hd = tgt(h.handler(), labelPos, nextInstr);
            if (hd < 0) continue;
            int bodyEndPc = crtBlockEnd(code, bci[hd]);
            if (bodyEndPc >= 0) {
                for (int i = 0; i < seq.size(); i++)          // first instruction past the clause
                    if (seq.get(i) instanceof Instruction && bci[i] > bodyEndPc) { leaders.add(i); break; }
                continue;
            }
            for (CodeElement ce : code) {
                if (!(ce instanceof LocalVariable lv)) continue;
                Integer st = labelPos.get(lv.startScope()), en = labelPos.get(lv.endScope());
                if (st == null || en == null) continue;
                if (nextInstr[st] == hd || (st >= hd && st <= hd + 2)) {   // the scope opened by this handler
                    int endInstr = nextInstr[en];
                    if (endInstr >= 0) leaders.add(endInstr);
                }
            }
        }

        // group consecutive instructions into blocks (new block at each leader)
        List<BasicBlock> blocks = new ArrayList<>();
        Map<Integer,Integer> blockOf = new HashMap<>();
        BasicBlock cur = null;
        for (int idx : instrIdx) {
            if (cur == null || leaders.contains(idx)) { cur = new BasicBlock(blocks.size()); blocks.add(cur); }
            cur.instrs.add(idx);
            blockOf.put(idx, cur.id);
        }

        // compute successors per block
        for (BasicBlock b : blocks) {
            int lastSeq = b.instrs.get(b.instrs.size() - 1);
            Instruction lastIn = (Instruction) seq.get(lastSeq);
            b.last = lastIn;
            int posOfLast = instrIdx.indexOf(lastSeq);
            int fallSeq = (posOfLast + 1 < instrIdx.size()) ? instrIdx.get(posOfLast + 1) : -1;
            Integer fallBlk = fallSeq >= 0 ? blockOf.get(fallSeq) : null;
            if (lastIn instanceof BranchInstruction br) {
                int tb = blockOf.getOrDefault(tgt(br.target(), labelPos, nextInstr), -1);
                if (ClassfileQueries.isConditionalBranch(br.opcode())) { b.branch = tb; if (fallBlk != null) b.fall = fallBlk; }
                else b.gotoTarget = tb;                          // GOTO
            } else if (lastIn instanceof java.lang.classfile.instruction.TableSwitchInstruction sw) {
                b.defaultCase = blockOf.getOrDefault(tgt(sw.defaultTarget(), labelPos, nextInstr), -1);
                for (var c : sw.cases()) b.cases.add(new int[]{ c.caseValue(), blockOf.getOrDefault(tgt(c.target(), labelPos, nextInstr), -1) });
            } else if (lastIn instanceof java.lang.classfile.instruction.LookupSwitchInstruction sw) {
                b.defaultCase = blockOf.getOrDefault(tgt(sw.defaultTarget(), labelPos, nextInstr), -1);
                for (var c : sw.cases()) b.cases.add(new int[]{ c.caseValue(), blockOf.getOrDefault(tgt(c.target(), labelPos, nextInstr), -1) });
            } else if (!(lastIn instanceof ReturnInstruction) && lastIn.opcode() != Opcode.ATHROW) {
                if (fallBlk != null) b.fall = fallBlk;           // ordinary fallthrough
            }
            // assemble dedup successor list (order: branch/goto/cases/default first, then fall)
            java.util.LinkedHashSet<Integer> s = new java.util.LinkedHashSet<>();
            if (b.branch >= 0) s.add(b.branch);
            if (b.gotoTarget >= 0) s.add(b.gotoTarget);
            for (int[] c : b.cases) if (c[1] >= 0) s.add(c[1]);
            if (b.defaultCase >= 0) s.add(b.defaultCase);
            if (b.fall >= 0) s.add(b.fall);
            b.succ.addAll(s);
        }
        for (BasicBlock b : blocks) for (int su : b.succ) blocks.get(su).preds.add(b.id);

        // resolve the exception table to block ids
        List<Trap> excs = new ArrayList<>();
        for (ExceptionCatch h : handlers) {
            Integer sBlk = blockOf.get(tgt(h.tryStart(), labelPos, nextInstr));
            Integer hBlk = blockOf.get(tgt(h.handler(), labelPos, nextInstr));
            int endSeq = tgt(h.tryEnd(), labelPos, nextInstr);
            int endBlkId = (endSeq < 0) ? blocks.size() : blockOf.getOrDefault(endSeq, blocks.size());
            ClassDesc type = h.catchType().map(ct -> ct.asSymbol()).orElse(null);
            if (sBlk != null && hBlk != null) excs.add(new Trap(sBlk, endBlkId, hBlk, type));
        }
        ControlFlowGraph built = new ControlFlowGraph(ClassfileQueries.simple(cm.thisClass().asInternalName()), m.methodName().stringValue(),
                seq, instrIdx, nextInstr, labelPos, blocks, blockOf, excs);
        // Resolve each catch clause's CRT extent to the block that follows it, so the structurer can stop the
        // catch region there instead of inferring the boundary.
        for (ExceptionCatch h : handlers) {
            if (h.catchType().isEmpty()) continue;          // finally: see the note on leaders above
            int hd = tgt(h.handler(), labelPos, nextInstr);
            if (hd < 0) continue;
            int endPc = crtBlockEnd(code, bci[hd]);
            if (endPc < 0) continue;
            for (int i = 0; i < seq.size(); i++)
                if (seq.get(i) instanceof Instruction && bci[i] > endPc) {
                    Integer hb = blockOf.get(hd), fb = blockOf.get(i);
                    if (hb != null && fb != null) built.catchEnd.put(hb, fb);
                    break;
                }
        }
        return built;
    }

    public static void printCfg(Path classFile, String method) throws Exception {
        ClassModel cm = ClassFile.of().parse(classFile);
        MethodModel m = cm.methods().stream()
                .filter(x -> x.methodName().stringValue().equals(method)).findFirst().orElseThrow();
        ControlFlowGraph cfg = buildCfg(cm, m);
        DominanceFinder d = analyze(cfg);
        System.out.println("method " + cfg.owner + "." + cfg.method + "   (" + cfg.blocks.size() + " blocks)\n");
        for (BasicBlock b : cfg.blocks) {
            System.out.println("B" + b.id + ":");
            for (int idx : b.instrs) System.out.printf("    %s%n", ((Instruction) cfg.seq.get(idx)).opcode());
            List<String> outs = b.succ.stream().map(x -> "B" + x).toList();
            System.out.println("  -> " + (outs.isEmpty() ? "(exit)" : String.join(", ", outs)));
            System.out.printf("     idom=%s ipdom=%s%s%n%n",
                    b.id == 0 ? "-" : "B" + d.idom[b.id],
                    d.ipdom[b.id] < 0 ? "exit" : "B" + d.ipdom[b.id],
                    d.isLoopHeader(b.id) ? "  LOOP-HEADER body=" + d.loops.get(b.id) + " latch=B" + d.loopTail.get(b.id) : "");
        }
    }

    static boolean isTerminator(Instruction in) {
        return in instanceof BranchInstruction || in instanceof ReturnInstruction
                || in instanceof java.lang.classfile.instruction.TableSwitchInstruction
                || in instanceof java.lang.classfile.instruction.LookupSwitchInstruction
                || in.opcode() == Opcode.ATHROW;
    }

    static List<Integer> branchTargets(Instruction in, Map<Label,Integer> labelPos, int[] nextInstr) {
        List<Integer> t = new ArrayList<>();
        switch (in) {
            case BranchInstruction b -> t.add(tgt(b.target(), labelPos, nextInstr));
            case java.lang.classfile.instruction.TableSwitchInstruction sw -> {
                t.add(tgt(sw.defaultTarget(), labelPos, nextInstr));
                for (var c : sw.cases()) t.add(tgt(c.target(), labelPos, nextInstr));
            }
            case java.lang.classfile.instruction.LookupSwitchInstruction sw -> {
                t.add(tgt(sw.defaultTarget(), labelPos, nextInstr));
                for (var c : sw.cases()) t.add(tgt(c.target(), labelPos, nextInstr));
            }
            default -> { }
        }
        return t;
    }

    static int tgt(Label l, Map<Label,Integer> labelPos, int[] nextInstr) {
        Integer pos = labelPos.get(l);
        return pos == null ? -1 : nextInstr[pos];
    }

    public static DominanceFinder analyze(ControlFlowGraph cfg) {
        int n = cfg.blocks.size();
        // forward dominators (root = entry block 0)
        List<List<Integer>> preds = new ArrayList<>();
        for (BasicBlock b : cfg.blocks) preds.add(b.preds);
        BitSet[] dom = domSets(n, 0, preds);
        int[] idom = idomFrom(dom, 0);

        // post-dominators: dominators on the reverse graph with a virtual exit at index n
        int X = n;
        List<List<Integer>> rpreds = new ArrayList<>();
        for (int i = 0; i <= n; i++) rpreds.add(new ArrayList<>());
        List<Integer> exits = new ArrayList<>();
        for (BasicBlock b : cfg.blocks) {
            if (b.succ.isEmpty()) { rpreds.get(b.id).add(X); exits.add(b.id); }   // reverse edge X -> exit
            else for (int s : b.succ) rpreds.get(b.id).add(s);                    // reverse pred of b is its succ
        }
        BitSet[] pdom;
        int[] ipdom = new int[n];
        if (exits.isEmpty()) {
            // No block reaches an exit (a method that only spins): the
            // reverse dataflow from the unreachable virtual exit would
            // produce arbitrary sets. No block lies on a path to the exit,
            // so no block post-dominates another.
            pdom = new BitSet[n + 1];
            for (int b = 0; b <= n; b++) { pdom[b] = new BitSet(); pdom[b].set(b); }
            java.util.Arrays.fill(ipdom, -1);
        } else {
            rpreds.get(X).addAll(exits);                                         // X's reverse-preds are the exits
            pdom = domSets(n + 1, X, rpreds);
            int[] ipdomFull = idomFrom(pdom, X);
            for (int b = 0; b < n; b++) ipdom[b] = (ipdomFull[b] == X) ? -1 : ipdomFull[b];
        }

        DominanceFinder d = new DominanceFinder(n, dom, idom, pdom, ipdom);
        // natural loops from back-edges (edge b -> h with h dominating b)
        for (BasicBlock b : cfg.blocks)
            for (int s : b.succ)
                if (d.dominates(s, b.id)) {              // s is a header, b is the latch
                    Set<Integer> body = naturalLoop(s, b.id, cfg.blocks);
                    d.loops.merge(s, body, (a, c) -> { a.addAll(c); return a; });
                    d.loopTail.put(s, b.id);
                }
        return d;
    }

    /** Iterative dominator-set dataflow. preds.get(b) = graph predecessors of b; root has dom = {root}. */
    static BitSet[] domSets(int n, int root, List<List<Integer>> preds) {
        BitSet[] dom = new BitSet[n];
        for (int i = 0; i < n; i++) { dom[i] = new BitSet(n); dom[i].set(0, n); }
        dom[root].clear(); dom[root].set(root);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int b = 0; b < n; b++) {
                if (b == root) continue;
                BitSet nd = new BitSet(n);
                boolean first = true;
                for (int p : preds.get(b)) {
                    if (first) { nd.or(dom[p]); first = false; } else nd.and(dom[p]);
                }
                if (first) nd.clear();   // unreachable node: only itself
                nd.set(b);
                if (!nd.equals(dom[b])) { dom[b] = nd; changed = true; }
            }
        }
        return dom;
    }

    /** Immediate dominator = the strict dominator with the largest dominator set (closest). */
    static int[] idomFrom(BitSet[] dom, int root) {
        int n = dom.length;
        int[] idom = new int[n];
        for (int b = 0; b < n; b++) {
            if (b == root) { idom[b] = root; continue; }
            int best = -1;
            for (int dd = dom[b].nextSetBit(0); dd >= 0; dd = dom[b].nextSetBit(dd + 1)) {
                if (dd == b) continue;
                if (best == -1 || dom[dd].cardinality() > dom[best].cardinality()) best = dd;
            }
            idom[b] = best;
        }
        return idom;
    }

    /** Natural loop of back-edge tail -> head: head plus everything reaching tail without leaving via head. */
    static Set<Integer> naturalLoop(int head, int tail, List<BasicBlock> blocks) {
        Set<Integer> loop = new HashSet<>();
        loop.add(head);
        Deque<Integer> stack = new ArrayDeque<>();
        if (loop.add(tail)) stack.push(tail);
        while (!stack.isEmpty()) {
            int m = stack.pop();
            for (int p : blocks.get(m).preds) if (p != head && loop.add(p)) stack.push(p);
        }
        return loop;
    }

    /** The local-variable slot referenced by an instruction, or -1 if none. */
    static int slotOf(CodeElement e) {
        if (e instanceof LoadInstruction li) return li.slot();
        if (e instanceof StoreInstruction si) return si.slot();
        if (e instanceof IncrementInstruction ii) return ii.slot();
        return -1;
    }

    /** Package the per-method direct-translation ingredients; null when {@code m} has no Code attribute. */
    public static MethodBody bodyOf(ClassModel cm, MethodModel m) {
        CodeAttribute code = (CodeAttribute) m.findAttribute(Attributes.code()).orElse(null);
        if (code == null) {
            return null;
        }
        MethodTypeDesc mt = m.methodTypeSymbol();

        Map<Integer, String> slotName = new HashMap<>();
        Map<Integer, String> slotDesc = new HashMap<>();
        List<LocalVariable> lvs = new ArrayList<>();
        List<LocalVariableType> lvts = new ArrayList<>();
        List<CodeElement> seq = new ArrayList<>();
        for (CodeElement e : code) {
            switch (e) {
                case LocalVariable lv -> {
                    slotName.put(lv.slot(), lv.name().stringValue());
                    slotDesc.put(lv.slot(), lv.type().stringValue());
                    lvs.add(lv);
                }
                case LocalVariableType lvt -> lvts.add(lvt);
                case Instruction i -> seq.add(i);
                case LabelTarget lt -> seq.add(lt);
                default -> { }
            }
        }

        ControlFlowGraph cfg = null;
        try {
            cfg = buildCfg(cm, m);
        } catch (RuntimeException ignore) {
            // straight-line fallback below
        }
        Locals locals = Locals.from(lvs, lvts, cfg != null ? cfg.labelPos : Map.of());

        // Seed `declared` with every parameter slot (statics start at 0, instance methods reserve slot 0
        // for `this`).
        boolean isStatic = m.flags().has(AccessFlag.STATIC);
        Set<String> declared = new HashSet<>();
        int slot = isStatic ? 0 : 1;
        for (int i = 0; i < mt.parameterCount(); i++) {
            ClassDesc pt = mt.parameterType(i);
            Locals.Scope ps = locals.at(slot, 0);
            declared.add(ps != null ? Locals.slotVarKey(locals, slot, 0) : ("v" + slot));
            String d = pt.descriptorString();
            slot += (d.equals("J") || d.equals("D")) ? 2 : 1;
        }

        List<CrtRange> crt = crtRanges(code);
        int[] seqToBci = bciTable(seq);
        Map<Integer, List<java.lang.classfile.Annotation>> slotAnnos = new HashMap<>();
        for (var ta : ClassfileQueries.typeAnnotationsOn(code)) {
            if (!ta.targetPath().isEmpty()) {
                continue;
            }
            if (ta.targetInfo() instanceof java.lang.classfile.TypeAnnotation.LocalVarTarget lv) {
                for (var entry : lv.table()) {
                    slotAnnos.computeIfAbsent(entry.index(), k -> new ArrayList<>()).add(ta.annotation());
                }
            }
        }
        String retDesc = mt.returnType().descriptorString();
        boolean isCtor = m.methodName().stringValue().equals("<init>");

        DominanceFinder dom = null;
        List<MethodBody.HoistedDecl> hoisted = List.of();
        if (cfg != null && cfg.blocks.size() > 1) {
            dom = analyze(cfg);
            hoisted = hoistedDeclInfos(cfg, dom, locals, slotName, slotDesc, declared, crt, seqToBci);
        }
        return new MethodBody(cfg, dom, locals, seq, slotName, slotDesc, declared,
                crt, LineNumbers.of(code), seqToBci, slotAnnos, retDesc, isCtor, isStatic, hoisted);
    }

    /**
     * The hoist detection: returns {@link MethodBody.HoistedDecl}
     * records (slot/first-store-pos/name/descriptor/generic) for the direct translator. Mutates
     * {@code preDeclared} identically (adds each hoisted key), so a hoisted local's first store becomes
     * an assignment rather than a re-declaration.
     */
    static List<MethodBody.HoistedDecl> hoistedDeclInfos(ControlFlowGraph cfg, DominanceFinder dom, Locals locals,
                                              Map<Integer, String> slotName, Map<Integer, String> slotDesc,
                                              Set<String> preDeclared, List<CrtRange> crt, int[] seqToBci) {
        // A debug-info-less input (no LVT) has no slot types at all; a local
        // whose first store does not dominate its uses must still hoist, as
        // an UNTYPED declaration, or the branch-scoped `var` leaves later
        // uses unresolved.
        boolean noDebugNames = (locals == null || locals.bySlot.isEmpty()) && slotName.isEmpty();
        Map<String, Integer> firstStoreBlk = new HashMap<>();
        Map<String, Integer> firstStorePos = new HashMap<>();
        Map<String, Integer> keySlot = new HashMap<>();
        Map<String, Set<Integer>> refBlocks = new HashMap<>();
        for (BasicBlock b : cfg.blocks) {
            for (int idx : b.instrs) {
                CodeElement e = cfg.seq.get(idx);
                int s = slotOf(e);
                if (s < 0) {
                    continue;
                }
                String key = Locals.slotVarKey(locals, s, idx);
                keySlot.putIfAbsent(key, s);
                refBlocks.computeIfAbsent(key, k -> new HashSet<>()).add(b.id);
                if (e instanceof StoreInstruction && !firstStoreBlk.containsKey(key)) {
                    firstStoreBlk.put(key, b.id);
                    firstStorePos.put(key, idx);
                }
            }
        }
        Set<Integer> handlerEntry = new HashSet<>();
        for (Trap ex : cfg.excs) {
            BasicBlock h = cfg.blocks.get(ex.handlerBlk());
            if (!h.instrs.isEmpty()) {
                handlerEntry.add(h.instrs.get(0));
            }
        }

        List<MethodBody.HoistedDecl> out = new ArrayList<>();
        for (String key : new java.util.TreeSet<>(firstStoreBlk.keySet())) {
            if (preDeclared.contains(key)) {
                continue;
            }
            if (handlerEntry.contains(firstStorePos.get(key))) {
                continue;
            }
            int slot = keySlot.get(key), dpos = firstStorePos.get(key);
            Locals.Scope sc = locals != null ? locals.at(slot, dpos) : null;
            if (sc == null && locals != null && locals.bySlot.containsKey(slot)) {
                continue;   // synthetic (e.g. monitor temp)
            }
            String genericType = sc != null ? sc.genericType() : null;
            String desc = sc != null ? sc.desc() : slotDesc.get(slot);
            if (genericType == null && desc == null) {
                if (!noDebugNames) {
                    continue;   // no known type -> leave to the first store's `var`
                }
                // Debug-info-less input: fall back to the store opcode's type
                // kind (reference types collapse to Object; int covers the
                // whole int family, which is also the bytecode's own view) --
                // except a boolean, which the surrounding call/return
                // signatures can recover.
                desc = storeKindDesc(cfg.seq.get(dpos));
                if (desc == null) {
                    continue;
                }
                slotDesc.put(slot, desc);   // the coercion oracle needs it too
            }
            int db = firstStoreBlk.get(key);
            boolean dominatesAll = refBlocks.get(key).stream().allMatch(rb -> dom.dominates(db, rb));
            if (!dominatesAll) {
                String nm = sc != null ? sc.name() : name(slotName, slot);
                out.add(new MethodBody.HoistedDecl(slot, dpos, nm, desc, genericType));
                preDeclared.add(key);
            }
        }
        return out;
    }

    static String name(Map<Integer,String> names, int slot) { return names.getOrDefault(slot, "v" + slot); }

    /** The field descriptor a store opcode implies, or null when unclassifiable. */
    private static String storeKindDesc(CodeElement e) {
        if (!(e instanceof StoreInstruction si)) {
            return null;
        }
        return switch (si.typeKind()) {
            case INT -> "I";
            case LONG -> "J";
            case FLOAT -> "F";
            case DOUBLE -> "D";
            case REFERENCE -> "Ljava/lang/Object;";
            default -> null;
        };
    }
}
