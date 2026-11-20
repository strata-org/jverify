package org.strata.java.bytecode.verifier.compiler;

import org.strata.java.bytecode.frontend.BasicBlock;
import org.strata.java.bytecode.frontend.ControlFlowGraph;
import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.StmtExpr;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Folds acyclic branching code that computes one value -- a ternary, a boolean
 * {@code &&}/{@code ||} -- into one conditional expression per branch, each arm the
 * value its path leaves on reaching the branch's immediate post-dominator. Arms
 * that read as booleans, or the {@code 1}/{@code 0} pair javac compiles a boolean
 * to, make a boolean conditional.
 */
final class ValueFolder {

    private final BodyTranslator in;
    private final ControlFlowGraph cfg;
    private final int[] ipdom;
    private final int stop;
    private int base = -1;
    private final Set<AstNode<StmtExpr>> folded;

    /** The region does not compute one value: the caller emits it as it is. */
    static final class NotAValue extends RuntimeException {
        NotAValue() {
            super(null, null, false, false);
        }
    }
    ValueFolder(BodyTranslator in, ControlFlowGraph cfg, int[] ipdom, int stop) {
        this(in, cfg, ipdom, stop, new HashSet<>());
    }

    private ValueFolder(BodyTranslator in, ControlFlowGraph cfg, int[] ipdom, int stop,
                        Set<AstNode<StmtExpr>> folded) {
        this.in = in;
        this.cfg = cfg;
        this.ipdom = ipdom;
        this.stop = stop;
        this.folded = folded;
    }

    /** The value the conditional ending block {@code b}, its operands translated, folds to. */
    AstNode<StmtExpr> branch(int b) {
        return from(b, new HashSet<>(), false);
    }

    private AstNode<StmtExpr> from(int b, Set<Integer> path, boolean translatePrefix) {
        if (b == stop) {
            if (in.stack.size() != base + 1) {
                throw new NotAValue();
            }
            return in.stack.pop();
        }
        if (b < 0 || !path.add(b)) {
            throw new NotAValue();
        }
        try {
            BasicBlock blk = cfg.blocks.get(b);
            List<Integer> idxs = blk.instrs;
            Instruction last = (Instruction) cfg.seq.get(idxs.get(idxs.size() - 1));
            boolean stops = last instanceof BranchInstruction || blk.isSwitch();
            if (translatePrefix) {
                List<CodeElement> ins = new ArrayList<>();
                for (int i : idxs) {
                    ins.add(cfg.seq.get(i));
                }
                in.translateSpan(ins, idxs, 0, stops ? ins.size() - 1 : ins.size());
            }
            if (blk.isSwitch()) {
                return switchValue(blk, path);
            }
            if (blk.gotoTarget >= 0) {
                return from(blk.gotoTarget, path, true);
            }
            if (!blk.isConditional()) {
                return from(blk.fall, path, true);
            }
            int join = ipdom[b];
            if (join > b && join < stop) {
                ValueFolder nested = new ValueFolder(in, cfg, ipdom, join, folded);
                int depth = in.stack.size();
                path.remove(b);
                AstNode<StmtExpr> v = nested.from(b, path, false);
                if (in.stack.size() != depth - operandsOf(last)) {
                    throw new NotAValue();
                }
                in.stack.push(v);
                return from(join, path, true);
            }
            in.pos = idxs.get(idxs.size() - 1);
            AstNode<StmtExpr> cond = in.materializeComparison(last.opcode());
            if (base < 0) {
                base = in.stack.size();
            }
            var saved = new ArrayList<>(in.stack);
            AstNode<StmtExpr> thenV = arm(blk.fall, path);
            in.stack.clear();
            in.stack.addAll(saved);
            AstNode<StmtExpr> elseV = arm(blk.branch, path);
            if (!(primitive(thenV) && primitive(elseV)) && !sameType(thenV, elseV)) {
                throw new NotAValue();
            }
            boolean bool = in.bools.contains(thenV) || in.bools.contains(elseV)
                    || LaurelTrees.isBooleanish(thenV.val()) || LaurelTrees.isBooleanish(elseV.val())
                    || (isIntLiteral(thenV, 1) && isIntLiteral(elseV, 0));
            if (bool) {
                thenV = in.coerce(thenV, "Z");
                elseV = in.coerce(elseV, "Z");
            }
            AstNode<StmtExpr> v;
            if (bool && isBool(thenV, true) && isBool(elseV, false)) {
                v = cond;
            } else if (bool && isBool(thenV, false) && isBool(elseV, true)) {
                v = in.e(LaurelFactory.primitiveOp(new org.strata.jverify.laurel.Operation.Not(), cond));
            } else {
                v = in.e(new StmtExpr.IfThenElse(cond, thenV, Optional.of(elseV)));
            }
            if (bool) {
                in.bools.add(v);
            }
            if (primitive(thenV) && primitive(elseV)) {
                folded.add(v);
            } else if (sameType(thenV, elseV) && in.staticTypeOf.get(thenV) != null) {
                in.staticTypeOf.put(v, in.staticTypeOf.get(thenV));
            }
            if (in.heapReadValues.contains(thenV) || in.heapReadValues.contains(elseV)) {
                in.heapReadValues.add(v);
            }
            if (in.isUnboundedInt(thenV) && in.isUnboundedInt(elseV)) {
                in.unboundedResults.add(v);
            }
            if (in.arrays.enabled()) {
                typeArrayValue(v, thenV, elseV);
            }
            return v;
        } finally {
            path.remove(b);
        }
    }

    /** A switch's value: {@code key == c ? arm : ...}, the default arm last. */
    private AstNode<StmtExpr> switchValue(BasicBlock blk, Set<Integer> path) {
        AstNode<StmtExpr> key = in.stack.pop();
        if (BodyTranslator.isStackRead(key)) {
            throw new NotAValue();
        }
        if (base < 0) {
            base = in.stack.size();
        }
        var saved = new ArrayList<>(in.stack);
        AstNode<StmtExpr> fallback = arm(blk.defaultCase, path);
        AstNode<StmtExpr> v = fallback;
        for (int i = blk.cases.size() - 1; i >= 0; i--) {
            int[] c = blk.cases.get(i);
            in.stack.clear();
            in.stack.addAll(saved);
            AstNode<StmtExpr> armV = arm(c[1], path);
            if (!(primitive(armV) && primitive(fallback)) && !sameType(armV, fallback)) {
                throw new NotAValue();
            }
            AstNode<StmtExpr> test = in.e(LaurelFactory.primitiveOp(new org.strata.jverify.laurel.Operation.Eq(),
                    in.e(key.val()), in.e(in.pinnedLongLiteral(c[0]))));
            v = in.e(new StmtExpr.IfThenElse(test, armV, Optional.of(v)));
            if (primitive(armV) && primitive(fallback)) {
                folded.add(v);
            }
        }
        return v;
    }

    private AstNode<StmtExpr> arm(int b, Set<Integer> path) {
        int mark = in.stmts.size();
        AstNode<StmtExpr> v = from(b, path, true);
        if (in.stmts.size() == mark) {
            return v;
        }
        var stmts = new ArrayList<>(in.stmts.subList(mark, in.stmts.size()));
        in.stmts.subList(mark, in.stmts.size()).clear();
        stmts.add(v);
        return in.e(new StmtExpr.Block(stmts, Optional.empty(), List.of(), Optional.empty()));
    }

    /**
     * An array-valued conditional: both arms must have the same array descriptor,
     * which the result then carries (so its element kind is known); arms of
     * different types make it an Object use. An arm that already carries its own
     * refusal is left to it.
     */
    private void typeArrayValue(AstNode<StmtExpr> r, AstNode<StmtExpr> thenV, AstNode<StmtExpr> elseV) {
        String td = in.descOf.get(thenV);
        String ed = in.descOf.get(elseV);
        boolean thenArray = td != null && td.startsWith("[");
        boolean elseArray = ed != null && ed.startsWith("[");
        if ((!thenArray && !elseArray) || in.deferredRefusal.containsKey(thenV)
                || in.deferredRefusal.containsKey(elseV)) {
            return;
        }
        if (!java.util.Objects.equals(td, ed)) {
            throw new JavaViolationException(ArrayModel.AS_OBJECT);
        }
        in.noteDesc(r, td);
        TypeRef t = in.staticTypeOf.get(thenV);
        if (t != null) {
            in.staticTypeOf.put(r, t);
        }
    }

    /** A primitive or boolean value: arms of these fold whatever their widths. */
    private boolean primitive(AstNode<StmtExpr> v) {
        String d = in.descOf.get(v);
        return folded.contains(v) || v.val() instanceof StmtExpr.LiteralInt || v.val() instanceof StmtExpr.LiteralBool
                || in.bools.contains(v) || LaurelTrees.isBooleanish(v.val()) || in.isUnboundedInt(v)
                || (d != null && "IJSBCZ".contains(d) && d.length() == 1);
    }

    /**
     * Both arms are arrays of one descriptor, or values of one declared class type,
     * so a conditional of them types as it. Other reference arms meet on the operand
     * stack, where their types are joined: the descriptors here are erased, and Laurel
     * may type the arms apart (a generic call's result at its argument's type).
     */
    private boolean sameType(AstNode<StmtExpr> a, AstNode<StmtExpr> b) {
        String da = in.descOf.get(a);
        if (da != null && da.startsWith("[")) {
            return da.equals(in.descOf.get(b));
        }
        TypeRef ta = in.staticTypeOf.get(a);
        return ta != null && ta.kind() == TypeRef.Kind.CLASS && ta.equals(in.staticTypeOf.get(b))
                && !in.typeVarValues.contains(a) && !in.typeVarValues.contains(b);
    }

    private static int operandsOf(Instruction br) {
        return switch (br.opcode()) {
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE -> 2;
            default -> 1;
        };
    }

    private static boolean isBool(AstNode<StmtExpr> v, boolean value) {
        return v.val() instanceof StmtExpr.LiteralBool lb && lb.value() == value;
    }

    private static boolean isIntLiteral(AstNode<StmtExpr> v, int value) {
        return v.val() instanceof StmtExpr.LiteralInt li && li.value() == value;
    }
}
