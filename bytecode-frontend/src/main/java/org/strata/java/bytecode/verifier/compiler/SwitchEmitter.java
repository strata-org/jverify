package org.strata.java.bytecode.verifier.compiler;

import org.strata.java.bytecode.frontend.BasicBlock;
import org.strata.java.bytecode.frontend.ControlFlowGraph;
import org.strata.java.bytecode.frontend.DominanceFinder;
import org.strata.java.bytecode.frontend.Span;
import org.strata.java.bytecode.frontend.StructException;
import org.strata.java.bytecode.frontend.Trap;
import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.CatchClause;
import org.strata.jverify.laurel.Operation;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.Variable;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
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

/**
 * Emits Laurel if-cascades from CFG switch regions: int and string switch
 * statements (two-phase string switches included) and switch expressions,
 * with arm recovery, multi-label {@code $orElse} folding, and selector
 * hoisting.
 */
final class SwitchEmitter {

    private final ControlFlowStructurer s;
    private final ControlFlowGraph cfg;
    private final DominanceFinder dom;
    private final BodyTranslator body;
    private final TranslationContext ctx;

    SwitchEmitter(ControlFlowStructurer s) {
        this.s = s;
        this.cfg = s.cfg;
        this.dom = s.dom;
        this.body = s.body;
        this.ctx = s.ctx;
    }

    /** Sentinel break label for a switch arm's trailing `break` (stripped by normalizeArm). */
    private static final String ARM_BREAK = "java?armbreak";

    /** One recovered arm: its label literals, default flag, and already-converted body statements. */
    private record Arm(List<AstNode<StmtExpr>> labels, boolean isDefault, List<AstNode<StmtExpr>> stmts) {
    }

    /** Translate the selector-producing instructions before a switch, leaving the value on the stack. */
    private AstNode<StmtExpr> switchSelector(BasicBlock b, List<AstNode<StmtExpr>> out, int keepFromEnd) {
        int mark = body.stmts.size();
        for (int i = 0; i < b.instrs.size() - keepFromEnd; i++) {
            body.pos = b.instrs.get(i);
            body.translate((Instruction) cfg.seq.get(b.instrs.get(i)));
        }
        s.drainInto(out, mark);
        return body.stack.isEmpty() ? body.e(new StmtExpr.LiteralString("?")) : body.stack.pop();
    }

    int emitSwitch(int swBlk, Set<Integer> stop, List<AstNode<StmtExpr>> out) {
        BasicBlock b = cfg.blocks.get(swBlk);
        AstNode<StmtExpr> selector = switchSelector(b, out, 1);
        int follow = dom.ipdom[swBlk];
        var arms = recoverArms(b, swBlk, stop, follow, v -> List.of(body.e(body.pinnedLongLiteral(v))));
        out.add(s.place(buildCascade(selector, arms), swBlk));
        return follow;
    }

    boolean isStringSwitch(BasicBlock b) {
        if (!b.isSwitch() || b.instrs.size() < 2 || b.defaultCase < 0) {
            return false;
        }
        Instruction sel = (Instruction) cfg.seq.get(b.instrs.get(b.instrs.size() - 2));
        if (!(sel instanceof InvokeInstruction inv) || !inv.name().stringValue().equals("hashCode")
                || !inv.typeSymbol().returnType().descriptorString().equals("I")) {
            return false;
        }
        return cfg.blocks.get(b.defaultCase).isSwitch();
    }

    int emitStringSwitch(int p1Blk, Set<Integer> stop, List<AstNode<StmtExpr>> out) {
        BasicBlock p1 = cfg.blocks.get(p1Blk);
        int n1 = p1.instrs.size();
        AstNode<StmtExpr> selector = switchSelector(p1, out, 2);   // keep `aload X . hashCode` .. lookupswitch

        int p2Blk = p1.defaultCase;
        BasicBlock p2 = cfg.blocks.get(p2Blk);
        s.visited[p2Blk] = true;
        // Pair each `ldc "s"` with the dense index it stores.
        Map<Integer, List<String>> idxToStrs = new HashMap<>();
        String lastStr = null;
        Integer lastInt = null;
        for (int id = p1Blk + 1; id < p2Blk; id++) {
            s.visited[id] = true;
            for (int si : cfg.blocks.get(id).instrs) {
                CodeElement e = cfg.seq.get(si);
                if (e instanceof java.lang.classfile.instruction.ConstantInstruction ci) {
                    Object v = ci.constantValue();
                    if (v instanceof String s) {
                        lastStr = s;
                    } else if (v instanceof Integer iv) {
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
        int follow = dom.ipdom[p2Blk];

        Integer vMerge = switchValueDiamond(p2Blk);
        if (vMerge != null) {
            body.stack.push(buildSwitchExprCascade(p2Blk, vMerge, selector,
                    v -> idxToStrs.getOrDefault(v, List.of()).stream()
                            .map(s -> body.e((StmtExpr) new StmtExpr.LiteralString(s))).toList()));
            return vMerge;
        }
        var arms = recoverArms(p2, p2Blk, stop, follow,
                v -> idxToStrs.getOrDefault(v, List.of()).stream()
                        .map(s -> body.e((StmtExpr) new StmtExpr.LiteralString(s))).toList());
        out.add(s.place(buildCascade(selector, arms), p1Blk));
        return follow;
    }

    /** Recover a statement switch's arms (region per head; break sentinel; fall-through refusal). */
    private List<Arm> recoverArms(BasicBlock b, int swBlk, Set<Integer> stop, int follow,
                                  java.util.function.IntFunction<List<AstNode<StmtExpr>>> labelsFor) {
        java.util.LinkedHashMap<Integer, List<Integer>> byTarget = new java.util.LinkedHashMap<>();
        for (int[] c : b.cases) {
            byTarget.computeIfAbsent(c[1], k -> new ArrayList<>()).add(c[0]);
        }
        int defBlk = b.defaultCase;
        Set<Integer> heads = new HashSet<>(byTarget.keySet());
        if (defBlk >= 0) {
            heads.add(defBlk);
        }
        List<Integer> order = new ArrayList<>(heads);
        Collections.sort(order);

        ControlFlowStructurer.Ctx sw = new ControlFlowStructurer.Ctx(false, -1, follow, -1, -1);
        sw.breakLabel = ARM_BREAK;   // arm breaks are stripped, not emitted as labeled Exit
        s.ctxStack.push(sw);
        var raw = new ArrayList<Arm>();
        for (int head : order) {
            Set<Integer> caseStop = new HashSet<>(stop);
            if (follow >= 0) {
                caseStop.add(follow);
            }
            for (int h : heads) {
                if (h != head) {
                    caseStop.add(h);
                }
            }
            List<AstNode<StmtExpr>> bodyStmts = s.region(head, caseStop);
            List<AstNode<StmtExpr>> labels = head != defBlk
                    ? labelsForValues(byTarget.get(head), labelsFor)
                    : List.of();
            raw.add(new Arm(labels, head == defBlk, bodyStmts));
        }
        s.ctxStack.pop();
        return normalizeArms(raw);
    }

    private List<AstNode<StmtExpr>> labelsForValues(List<Integer> values,
                                                    java.util.function.IntFunction<List<AstNode<StmtExpr>>> labelsFor) {
        var out = new ArrayList<AstNode<StmtExpr>>();
        for (int v : values) {
            out.addAll(labelsFor.apply(v));
        }
        return out;
    }

    /**
     * Normalize the switch arms: merge label-only (empty-body) arms
     * into the next via {@code pending}, strip each arm's trailing sentinel
     * break, and refuse real fall-through.
     */
    private List<Arm> normalizeArms(List<Arm> arms) {
        var out = new ArrayList<Arm>();
        var pendingLabels = new ArrayList<AstNode<StmtExpr>>();
        boolean pendingDefault = false;
        for (int i = 0; i < arms.size(); i++) {
            Arm arm = arms.get(i);
            pendingLabels.addAll(arm.labels());
            pendingDefault |= arm.isDefault();
            var stmts = new ArrayList<>(arm.stmts());
            if (stmts.isEmpty()) {
                continue;   // label-only group: merges with the next
            }
            if (isArmBreak(stmts.get(stmts.size() - 1))) {
                stmts.remove(stmts.size() - 1);
            } else if (i < arms.size() - 1
                    && !(stmts.get(stmts.size() - 1).val() instanceof StmtExpr.Return)
                    && !(stmts.get(stmts.size() - 1).val() instanceof StmtExpr.Throw)) {
                throw new JavaViolationException("Fall-through in switch is not supported");
            }
            out.add(new Arm(new ArrayList<>(pendingLabels), pendingDefault, stmts));
            pendingLabels.clear();
            pendingDefault = false;
        }
        if (!pendingLabels.isEmpty() || pendingDefault) {
            out.add(new Arm(new ArrayList<>(pendingLabels), pendingDefault, List.of()));
        }
        return out;
    }

    private static boolean isArmBreak(AstNode<StmtExpr> n) {
        return n.val() instanceof StmtExpr.Exit ex && ex.target().equals(ARM_BREAK);
    }

    /** Build the if-cascade: default = final else, multi-label $orElse of $eq, hoist selector. */
    private AstNode<StmtExpr> buildCascade(AstNode<StmtExpr> selector, List<Arm> arms) {
        boolean hoist = !isTrivialSelector(selector);
        String selName = null;
        AstNode<StmtExpr> selValue = null;
        if (hoist) {
            selName = "java?sel" + (ctx.switchSelCounter++);
            selValue = selector;
        }
        Arm defaultArm = null;
        var cases = new ArrayList<Arm>();
        for (Arm a : arms) {
            if (a.isDefault()) {
                defaultArm = a;
            } else {
                cases.add(a);
            }
        }
        AstNode<StmtExpr> cascade = defaultArm != null ? armBody(defaultArm) : null;
        for (int i = cases.size() - 1; i >= 0; i--) {
            Arm arm = cases.get(i);
            if (arm.labels().isEmpty()) {
                // No selector value reaches this arm; an IfThenElse with a
                // null condition would be an invalid tree. Bail to the
                // linear form rather than emit it.
                throw new StructException("switch arm with no labels");
            }
            AstNode<StmtExpr> cond = null;
            for (AstNode<StmtExpr> label : arm.labels()) {
                AstNode<StmtExpr> sel = hoist ? body.e(LaurelFactory.var_(selName)) : freshCopy(selector);
                AstNode<StmtExpr> eq = body.e(LaurelFactory.primitiveOp(new Operation.Eq(), sel, label));
                cond = cond == null ? eq
                        : body.e(LaurelFactory.primitiveOp(new Operation.OrElse(), cond, eq));
            }
            cascade = body.e(new StmtExpr.IfThenElse(cond, armBody(arm),
                    cascade != null ? Optional.of(cascade) : Optional.empty()));
        }
        if (cascade == null) {
            cascade = body.e(new StmtExpr.Block(List.of(), Optional.empty()));
        }
        if (!hoist) {
            return cascade;
        }
        var typeNode = LaurelFactory.node((org.strata.jverify.laurel.HighType)
                new org.strata.jverify.laurel.HighType.UserDefined(LaurelFactory.ident("int32")));
        var declared = new org.strata.jverify.laurel.Variable.Declare(
                new org.strata.jverify.laurel.ParameterOpt(LaurelFactory.ident(selName), Optional.of(typeNode)));
        AstNode<StmtExpr> bind = body.e(new StmtExpr.Assign(
                List.of(new AstNode<>(declared, SourceMap.UNKNOWN_RANGE)), selValue));
        return body.e(new StmtExpr.Block(List.of(bind, cascade), Optional.empty()));
    }

    /** A statement arm's body value: single statement unwrapped, else a block. */
    private AstNode<StmtExpr> armBody(Arm arm) {
        if (arm.stmts().size() == 1) {
            return arm.stmts().get(0);
        }
        return body.e(new StmtExpr.Block(new ArrayList<>(arm.stmts()), Optional.empty()));
    }

    private boolean isTrivialSelector(AstNode<StmtExpr> selector) {
        return (selector.val() instanceof StmtExpr.Var v
                && v.var_() instanceof org.strata.jverify.laurel.Variable.Local)
                || selector.val() instanceof StmtExpr.LiteralInt;
    }

    private AstNode<StmtExpr> freshCopy(AstNode<StmtExpr> n) {
        return body.e(n.val());
    }

    Integer switchValueDiamond(int swBlk) {
        BasicBlock b = cfg.blocks.get(swBlk);
        if (!b.isSwitch() || b.defaultCase < 0) {
            return null;
        }
        int merge = dom.ipdom[swBlk];
        if (merge < 0) {
            return null;
        }
        Set<Integer> heads = new HashSet<>();
        for (int[] c : b.cases) {
            heads.add(c[1]);
        }
        heads.add(b.defaultCase);
        for (int h : heads) {
            if (h == merge) {
                return null;
            }
            if (s.armIsValue(h, merge, new HashSet<>())) {
                continue;
            }
            if (!dom.pdom[h].get(merge) || !mergeConsumesValue(merge)) {
                return null;
            }
        }
        return merge;
    }

    boolean mergeConsumesValue(int merge) {
        BasicBlock m = cfg.blocks.get(merge);
        if (m.instrs.isEmpty()) {
            return false;
        }
        Instruction first = (Instruction) cfg.seq.get(m.instrs.get(0));
        return first instanceof StoreInstruction
                || first instanceof java.lang.classfile.instruction.ArrayStoreInstruction
                || (first instanceof java.lang.classfile.instruction.ReturnInstruction r
                        && r.opcode() != Opcode.RETURN)
                || (first instanceof FieldInstruction fi
                        && (fi.opcode() == Opcode.PUTFIELD || fi.opcode() == Opcode.PUTSTATIC))
                || first instanceof InvokeInstruction;
    }

    void emitSwitchExpr(int swBlk, int merge, List<AstNode<StmtExpr>> out) {
        BasicBlock b = cfg.blocks.get(swBlk);
        AstNode<StmtExpr> selector = switchSelector(b, out, 1);
        body.stack.push(buildSwitchExprCascade(swBlk, merge, selector,
                v -> List.of(body.e(body.pinnedLongLiteral(v)))));
    }

    /** Build a switch-expression cascade: each arm a single value (block arms refuse). */
    private AstNode<StmtExpr> buildSwitchExprCascade(int swBlk, int merge, AstNode<StmtExpr> selector,
                                                     java.util.function.IntFunction<List<AstNode<StmtExpr>>> labelsFor) {
        BasicBlock b = cfg.blocks.get(swBlk);
        java.util.LinkedHashMap<Integer, List<Integer>> byTarget = new java.util.LinkedHashMap<>();
        for (int[] c : b.cases) {
            byTarget.computeIfAbsent(c[1], k -> new ArrayList<>()).add(c[0]);
        }
        int defBlk = b.defaultCase;
        Set<Integer> heads = new HashSet<>(byTarget.keySet());
        heads.add(defBlk);
        List<Integer> order = new ArrayList<>(heads);
        Collections.sort(order);

        var arms = new ArrayList<Arm>();
        for (int head : order) {
            AstNode<StmtExpr> value;
            if (s.armIsValue(head, merge, new HashSet<>())) {
                value = s.evalArm(head, merge);
            } else {
                throw new JavaViolationException("Unsupported switch-expression arm (only single expressions)");
            }
            List<AstNode<StmtExpr>> labels = head != defBlk
                    ? labelsForValues(byTarget.get(head), labelsFor) : List.of();
            arms.add(new Arm(labels, head == defBlk, List.of(value)));
        }
        return buildCascade(selector, arms);
    }
}
