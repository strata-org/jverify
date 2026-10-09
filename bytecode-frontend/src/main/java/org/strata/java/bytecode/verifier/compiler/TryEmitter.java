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
import org.strata.jverify.laurel.HighType;
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
import java.util.TreeMap;

/**
 * Emits Laurel try/catch/finally from exception-table regions: nesting
 * levels, catch-arm recovery (multi-catch as {@code is}-guard disjunction),
 * finally-copy recognition and stripping, return-through-finally recovery —
 * and the synchronized monitor shape, which structures its body and then
 * refuses (Laurel has no synchronized statement).
 */
final class TryEmitter {

    private final ControlFlowStructurer s;
    private final ControlFlowGraph cfg;
    private final DominanceFinder dom;
    private final BodyTranslator body;
    private final TranslationContext ctx;

    TryEmitter(ControlFlowStructurer s) {
        this.s = s;
        this.cfg = s.cfg;
        this.dom = s.dom;
        this.body = s.body;
        this.ctx = s.ctx;
    }

    /**
     * A recognized finally handler: {@code astore v; <F>; aload v; athrow}
     * where {@code <F>} may span several layout-contiguous blocks. {@code
     * hEnd} is the block whose tail is the {@code aload v; athrow} rethrow;
     * {@code len} is the flattened instruction count of {@code <F>}.
     */
    record FinallyShape(int hb, int hEnd, int len, int slot) {
    }

    /** Split a try's exception entries into nesting levels (outermost last), or a single level. */
    List<List<Trap>> nestLevels(List<Trap> excs) {
        List<List<Trap>> one = List.of(new ArrayList<>(excs));
        TreeMap<Integer, List<Trap>> byEnd = new TreeMap<>();
        for (Trap e : excs) {
            byEnd.computeIfAbsent(e.endBlkId(), k -> new ArrayList<>()).add(e);
        }
        if (byEnd.size() < 2) {
            return one;
        }
        List<List<Trap>> levels = new ArrayList<>(byEnd.values());
        for (int i = 0; i + 1 < levels.size(); i++) {
            int innerH = levels.get(i).get(0).handlerBlk();
            for (Trap o : levels.get(i + 1)) {
                if (innerH < o.startBlk() || innerH >= o.endBlkId()) {
                    return one;
                }
            }
        }
        return levels;
    }

    int emitTry(int startBlk, Set<Integer> stop, List<AstNode<StmtExpr>> out) {
        s.emittedTries.add(startBlk);
        List<List<Trap>> levels = nestLevels(s.triesByStart.get(startBlk));
        return emitTryLevel(startBlk, levels, levels.size() - 1, stop, out);
    }

    /** One handler group awaiting finally-tail stripping before it becomes a Laurel CatchClause. */
    private record PendingCatch(String binding, List<String> caught, List<AstNode<StmtExpr>> body, int hb) {
    }

    /** Emit nesting level {@code li} (outermost last); its body is level {@code li - 1}, or the region. */
    int emitTryLevel(int startBlk, List<List<Trap>> levels, int li, Set<Integer> stop,
                     List<AstNode<StmtExpr>> out) {
        List<Trap> excs = levels.get(li);

        LinkedHashMap<Integer, List<Trap>> byHandler = new LinkedHashMap<>();
        FinallyShape fshape = null;
        int finallyBlk = -1, finallyEndId = -1;
        for (Trap e : excs) {
            FinallyShape s;
            if (e.type() == null && finallyBlk < 0 && (s = finallyShape(e.handlerBlk())) != null) {
                fshape = s;
                finallyBlk = e.handlerBlk();
                finallyEndId = e.endBlkId();
            } else {
                byHandler.computeIfAbsent(e.handlerBlk(), k -> new ArrayList<>()).add(e);
            }
        }

        boolean noCopy = fshape != null
                && finallyEndId >= fshape.hb() && finallyEndId <= fshape.hEnd();
        boolean armsBelow = !noCopy && fshape != null
                && levels.stream().flatMap(List::stream).anyMatch(e -> e.type() != null);

        int copyFollow = Integer.MIN_VALUE;
        if (fshape != null && !noCopy && !armsBelow) {
            copyFollow = consumeFinallyCopy(fshape, finallyEndId);
            if (copyFollow == Integer.MIN_VALUE) {
                byHandler.computeIfAbsent(finallyBlk, k -> new ArrayList<>())
                        .add(new Trap(startBlk, finallyEndId, finallyBlk, null));
                fshape = null;
                finallyBlk = -1;
                finallyEndId = -1;
            }
        }

        Set<Integer> handlerBlks = new HashSet<>(byHandler.keySet());
        if (fshape != null) {
            for (int b = fshape.hb(); b <= fshape.hEnd(); b++) {
                handlerBlks.add(b);
            }
        }

        int catchEndId = byHandler.isEmpty()
                ? finallyEndId : byHandler.values().iterator().next().get(0).endBlkId();
        int follow;
        Set<Integer> bodyStop = new HashSet<>(stop);
        bodyStop.addAll(handlerBlks);
        if (fshape != null && !noCopy) {
            bodyStop.add(finallyEndId);
        }

        List<AstNode<StmtExpr>> finallyBody = null;
        if (finallyBlk >= 0) {
            final int fh = finallyBlk;
            for (var it = s.triesByStart.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                if (en.getKey() != startBlk) {
                    en.getValue().removeIf(e -> e.type() == null && e.handlerBlk() == fh);
                    if (en.getValue().isEmpty()) {
                        it.remove();
                    }
                }
            }
            if (noCopy) {
                follow = tryFollow(startBlk, Math.min(finallyEndId, fshape.hb()), handlerBlks);
            } else if (armsBelow) {
                follow = s.chainExit(finallyEndId);
                s.markChainVisited(finallyEndId, follow);
                bodyStop.add(finallyEndId);
            } else {
                follow = copyFollow;
            }
            finallyBody = extractFinally(fshape);
        } else {
            follow = tryFollow(startBlk, catchEndId, handlerBlks);
            int crtFollow = -1;
            for (int hb : byHandler.keySet()) {
                Integer ce = cfg.catchEnd.get(hb);
                if (ce != null && (crtFollow < 0 || ce > crtFollow)) {
                    crtFollow = ce;
                }
            }
            if (crtFollow >= 0) {
                follow = crtFollow;
            }
        }
        if (follow >= 0) {
            bodyStop.add(follow);
        }

        // The catchers' types are in scope around the try BODY only (a throw
        // in a catch arm or finalizer answers to the next try out):
        // throw-coverage checks fire inline at ATHROW.
        List<String> armTypes = new ArrayList<>();
        for (var en : byHandler.entrySet()) {
            armTypes.addAll(caughtFqns(en.getValue()));
        }
        ctx.method.enclosingCatchTypes.push(armTypes);
        List<AstNode<StmtExpr>> tryBody;
        try {
            if (li > 0) {
                tryBody = new ArrayList<>();
                int innerFollow = emitTryLevel(startBlk, levels, li - 1, bodyStop, tryBody);
                if (innerFollow >= 0 && !bodyStop.contains(innerFollow)) {
                    tryBody.addAll(s.region(innerFollow, bodyStop));
                }
            } else {
                tryBody = new ArrayList<>(s.region(startBlk, bodyStop));
            }

            // A `return` crossing the finally: javac stashes the value in a temp,
            // runs the inlined copy, then loads and returns. When the shape is
            // exactly `var TMP = <expr>` + `return TMP`, the original `return
            // <expr>` is recovered outright.
            if (fshape != null && follow >= 0 && s.skipLead.containsKey(follow)
                    && cfg.blocks.get(follow).succ.isEmpty()) {
                Set<Integer> tailStop = new HashSet<>(bodyStop);
                tailStop.remove(follow);
                List<AstNode<StmtExpr>> tail = s.region(follow, tailStop);
                AstNode<StmtExpr> recovered = recoverReturnThroughFinally(tryBody, tail);
                if (recovered != null) {
                    tryBody.set(tryBody.size() - 1, recovered);
                } else {
                    tryBody.addAll(tail);
                }
                follow = -1;
            }

            // `try { return f(); }` leaves its value on the operand stack: the
            // ireturn lands in the follow block, but the call belongs to the body
            // (only for a typed catch; a catch-all here is a monitorexit guard).
            boolean typedCatch = byHandler.values().stream().flatMap(List::stream)
                    .anyMatch(e -> e.type() != null);
            if (typedCatch && follow >= 0 && follow < cfg.blocks.size() && !body.stack.isEmpty()) {
                BasicBlock fb = cfg.blocks.get(follow);
                if (fb.instrs.size() == 1 && fb.isReturnOrThrow()) {
                    tryBody.addAll(s.region(follow, stop));
                    follow = -1;
                }
            }
        } finally {
            ctx.method.enclosingCatchTypes.pop();
        }

        // Recover each catch arm's body (still mutable), so its inlined
        // finally-tail copy can be stripped before it becomes an immutable
        // Laurel CatchClause.
        List<PendingCatch> pending = new ArrayList<>();
        for (var en : byHandler.entrySet()) {
            int hb = en.getKey();
            BasicBlock h = cfg.blocks.get(hb);
            Instruction first = (Instruction) cfg.seq.get(h.instrs.get(0));
            String varName = "e";
            int skip = 0;
            int mintedSlot = -1;
            int bindingSlot = -1;
            if (first instanceof StoreInstruction si) {
                body.pos = h.instrs.get(0);
                boolean unnamed = body.locals != null && body.locals.at(si.slot(), body.pos) == null;
                varName = unnamed ? body.freshName(si.slot()) : body.localName(si.slot());
                if (unnamed) {
                    mintedSlot = si.slot();
                }
                body.declared.add(body.varKey(si.slot()));
                bindingSlot = si.slot();
                skip = 1;
            } else if (first instanceof StackInstruction sk && sk.opcode() == Opcode.POP) {
                skip = 1;
            }
            List<String> caught = caughtFqns(en.getValue());
            s.skipLead.put(hb, skip);
            Set<Integer> catchStop = new HashSet<>(stop);
            if (follow >= 0) {
                catchStop.add(follow);
            }
            Integer crtEnd = cfg.catchEnd.get(hb);
            if (crtEnd != null) {
                catchStop.remove(follow);
                catchStop.add(crtEnd);
            }
            String armType = bindingSlot >= 0 ? armTypeOf(caught) : null;
            if (armType != null) {
                body.catchCasts.put(bindingSlot, new BodyTranslator.CatchCast(varName,
                        LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(armType)))));
            }
            List<AstNode<StmtExpr>> catchBody;
            try {
                catchBody = new ArrayList<>(s.region(hb, catchStop));
            } finally {
                body.catchCasts.remove(bindingSlot);
            }
            if (mintedSlot >= 0) {
                body.slotAlias.remove(mintedSlot);
            }
            pending.add(new PendingCatch(varName, caught, catchBody, hb));
        }

        // Strip the inlined finally copy from each normal exit (catch arm
        // tails, and a directly-nested try's arms): the single finally clause
        // is the only occurrence.
        if (finallyBody != null && !finallyBody.isEmpty()) {
            for (PendingCatch pc : pending) {
                stripFinallyTail(pc.body(), finallyBody);
            }
            if (!tryBody.isEmpty() && tryBody.get(tryBody.size() - 1).val() instanceof StmtExpr.Try inner) {
                AstNode<StmtExpr> rebuilt = stripNestedTryFinallyTails(
                        tryBody.get(tryBody.size() - 1), inner, finallyBody);
                if (rebuilt != null) {
                    tryBody.set(tryBody.size() - 1, rebuilt);
                }
            }
        }

        List<CatchClause> clauses = new ArrayList<>();
        for (PendingCatch pc : pending) {
            clauses.add(buildCatch(pc.binding(), pc.caught(), pc.body(), pc.hb()));
        }

        // The wrapper blocks stay UNPLACED: the final placement stamps them
        // with the try's own statement range (nested statements keep their
        // own ranges).
        AstNode<StmtExpr> tryBodyBlock = s.blockOf(tryBody);
        Optional<AstNode<StmtExpr>> finallyArm = finallyBody != null
                ? Optional.of(s.blockOf(finallyBody))
                : Optional.empty();
        StmtExpr tryNode = new StmtExpr.Try(tryBodyBlock, clauses, finallyArm);
        out.add(s.place(body.e(tryNode), startBlk));
        return follow;
    }

    /**
     * Recover `var TMP = <expr>; return TMP` (javac's return-through-finally
     * shape) back to a single return: if {@code tail} is a lone {@code return
     * TMP} and the last body statement declares {@code TMP} with an
     * initializer, return the recovered {@code return <expr>} (carrying the
     * declaration's range); else null.
     */
    private AstNode<StmtExpr> recoverReturnThroughFinally(List<AstNode<StmtExpr>> tryBody,
                                                          List<AstNode<StmtExpr>> tail) {
        if (tail.size() != 1 || tryBody.isEmpty()) {
            return null;
        }
        if (!(tail.get(0).val() instanceof StmtExpr.Return ret) || ret.value().isEmpty()) {
            return null;
        }
        if (!(ret.value().get().val() instanceof StmtExpr.Var rv)
                || !(rv.var_() instanceof Variable.Local rloc)) {
            return null;
        }
        AstNode<StmtExpr> lastDecl = tryBody.get(tryBody.size() - 1);
        if (!(lastDecl.val() instanceof StmtExpr.Assign asg) || asg.targets().size() != 1) {
            return null;
        }
        if (!(asg.targets().get(0).val() instanceof Variable.Declare decl)
                || !decl.parameter().name().text().equals(rloc.name().text())) {
            return null;
        }
        return ctx.placeStatement(body.e(new StmtExpr.Return(Optional.of(asg.value()))), lastDecl.source());
    }

    /** Rebuild a nested try with the finally-tail stripped from each of its catch arms, or null if unchanged. */
    private AstNode<StmtExpr> stripNestedTryFinallyTails(AstNode<StmtExpr> node, StmtExpr.Try inner,
                                                         List<AstNode<StmtExpr>> finallyBody) {
        List<CatchClause> rebuilt = new ArrayList<>();
        boolean changed = false;
        for (CatchClause c : inner.catches()) {
            if (c.body().val() instanceof StmtExpr.Block blk) {
                List<AstNode<StmtExpr>> s = new ArrayList<>(blk.statements());
                int before = s.size();
                stripFinallyTail(s, finallyBody);
                if (s.size() != before) {
                    changed = true;
                    AstNode<StmtExpr> newBody = new AstNode<>(
                            new StmtExpr.Block(s, blk.label()), c.body().source());
                    rebuilt.add(new CatchClause(c.binding(), c.predicate(), newBody, c.bindingType()));
                    continue;
                }
            }
            rebuilt.add(c);
        }
        if (!changed) {
            return null;
        }
        return new AstNode<>(new StmtExpr.Try(inner.body(), rebuilt, inner.finally_()), node.source());
    }

    /** The caught classes of a handler group (several for a multi-catch; Throwable for catch-all). */
    private List<String> caughtFqns(List<Trap> group) {
        List<String> caught = new ArrayList<>();
        for (Trap e : group) {
            caught.add(e.type() == null
                    ? ctx.resolver.resolveClassFqn("Throwable")
                    : body.resolveFqn(TypeRef.ofClassDesc(e.type())));
        }
        return caught;
    }

    /**
     * A catch arm as a Laurel CatchClause:
     * the binding keeps its name, type dispatch becomes the guard {@code
     * binding is E} (a multi-catch is the disjunction), the hierarchy up to
     * Throwable is registered per caught fqn, and bindingType is Unknown.
     */
    private CatchClause buildCatch(String binding, List<String> caught,
                                   List<AstNode<StmtExpr>> catchBody, int hb) {
        StmtExpr predicate = null;
        for (String fqn : caught) {
            ctx.composites.registerHierarchyUpToThrowable(fqn);
            StmtExpr test = new StmtExpr.IsType(
                    body.e(LaurelFactory.var_(binding)),
                    LaurelFactory.node(new HighType.UserDefined(LaurelFactory.ident(fqn))));
            predicate = predicate == null
                    ? test
                    : LaurelFactory.primitiveOp(new Operation.Or(), body.e(predicate), body.e(test));
        }
        return new CatchClause(LaurelFactory.ident(binding),
                Optional.of(body.e(predicate)),
                s.blockOf(catchBody),
                LaurelFactory.node(new HighType.Unknown()));
    }

    /**
     * The arm's type as Java types its binding, or null when an alternative's
     * superclasses are unknown (a class off the verifier's class path): the binding
     * is then read uncast.
     */
    private String armTypeOf(List<String> caught) {
        String arm = caught.getFirst();
        for (String fqn : caught) {
            arm = arm == null ? null : ctx.resolver.commonSuperclass(fqn, arm);
        }
        return arm;
    }

    /** Remove {@code f}'s statements from the tail of {@code stmts} when they match 1:1 (mod ranges). */
    private void stripFinallyTail(List<AstNode<StmtExpr>> stmts, List<AstNode<StmtExpr>> f) {
        int n = stmts.size(), k = f.size();
        if (k == 0 || n < k) {
            return;
        }
        for (int i = 0; i < k; i++) {
            if (!LaurelTrees.structuralEquals(stmts.get(n - k + i), f.get(i))) {
                return;
            }
        }
        for (int i = 0; i < k; i++) {
            stmts.remove(stmts.size() - 1);
        }
    }

    /** The handler's finally shape, or null when {@code hb} does not have it. */
    FinallyShape finallyShape(int hb) {
        BasicBlock h = cfg.blocks.get(hb);
        if (h.instrs.isEmpty()) {
            return null;
        }
        if (!(cfg.seq.get(h.instrs.get(0)) instanceof StoreInstruction si)) {
            return null;
        }
        int slot = si.slot();
        int count = -1;
        for (int b = hb; b < cfg.blocks.size(); b++) {
            BasicBlock blk = cfg.blocks.get(b);
            int n = blk.instrs.size();
            if (n >= 2
                    && ((Instruction) cfg.seq.get(blk.instrs.get(n - 1))).opcode() == Opcode.ATHROW
                    && cfg.seq.get(blk.instrs.get(n - 2)) instanceof LoadInstruction ld
                    && ld.slot() == slot) {
                return new FinallyShape(hb, b, count + n - 2, slot);
            }
            if (blk.succ.isEmpty()) {
                return null;
            }
            count += n;
        }
        return null;
    }

    /** The flattened finally-body opcodes of a handler (astore and trailing aload;athrow excluded). */
    private List<Opcode> finallyOpcodes(FinallyShape fs) {
        List<Opcode> ops = new ArrayList<>();
        for (int b = fs.hb(); b <= fs.hEnd(); b++) {
            BasicBlock blk = cfg.blocks.get(b);
            int from = b == fs.hb() ? 1 : 0;
            int to = b == fs.hEnd() ? blk.instrs.size() - 2 : blk.instrs.size();
            for (int i = from; i < to; i++) {
                ops.add(((Instruction) cfg.seq.get(blk.instrs.get(i))).opcode());
            }
        }
        return ops;
    }

    /**
     * Consume the inlined normal-path copy of a finally body starting at
     * block {@code c0}: verify the opcode sequence matches, mark consumed
     * blocks visited, and return the follow block, or {@link
     * Integer#MIN_VALUE} when the copy does not match.
     */
    int consumeFinallyCopy(FinallyShape fs, int c0) {
        List<Opcode> want = finallyOpcodes(fs);
        if (want.isEmpty() || c0 >= cfg.blocks.size()) {
            return Integer.MIN_VALUE;
        }
        int at = 0;
        for (int b = c0; b < cfg.blocks.size() && at < want.size(); b++) {
            BasicBlock blk = cfg.blocks.get(b);
            int i = 0;
            for (; i < blk.instrs.size() && at < want.size(); i++, at++) {
                if (((Instruction) cfg.seq.get(blk.instrs.get(i))).opcode() != want.get(at)) {
                    return Integer.MIN_VALUE;
                }
            }
            if (at < want.size()) {
                s.visited[b] = true;
                continue;
            }
            if (i < blk.instrs.size()) {
                s.skipLead.put(b, i);
                return b;
            }
            s.visited[b] = true;
            return blk.gotoTarget >= 0 ? blk.gotoTarget : blk.fall;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Structure the finally body from its handler: the leading astore and the
     * trailing {@code aload; athrow} rethrow are hidden via skipLead/skipTail,
     * and the blocks between are structured as an ordinary region.
     */
    List<AstNode<StmtExpr>> extractFinally(FinallyShape fs) {
        s.skipLead.merge(fs.hb(), 1, Integer::sum);
        s.skipTail.put(fs.hEnd(), 2);
        return s.region(fs.hb(), Set.of());
    }

    /** The block where a try's normal exit and its catches reconverge (resolving trivial goto bridges). */
    int tryFollow(int startBlk, int endId, Set<Integer> handlerBlks) {
        int follow = -1;
        for (int id = startBlk; id < endId && id < cfg.blocks.size(); id++) {
            for (int su : cfg.blocks.get(id).succ) {
                if ((su < startBlk || su >= endId) && !handlerBlks.contains(su)
                        && (follow < 0 || su < follow)) {
                    follow = su;
                }
            }
        }
        if (follow < 0) {
            for (int h : handlerBlks) {
                if (!hasNamedParam(h)) {
                    continue;
                }
                int after = dom.ipdom[h];
                if (after >= 0 && !handlerBlks.contains(after) && (follow < 0 || after < follow)) {
                    follow = after;
                }
            }
        }
        Set<Integer> seen = new HashSet<>();
        while (follow >= 0 && seen.add(follow) && s.isPureGoto(cfg.blocks.get(follow))) {
            follow = cfg.blocks.get(follow).gotoTarget;
        }
        return follow;
    }

    /** True if this handler's exception parameter is in the LocalVariableTable (written in source). */
    boolean hasNamedParam(int handlerBlk) {
        BasicBlock h = cfg.blocks.get(handlerBlk);
        if (h.instrs.isEmpty() || body.locals == null) {
            return false;
        }
        int at = h.instrs.get(0);
        return cfg.seq.get(at) instanceof StoreInstruction si && body.locals.at(si.slot(), at) != null;
    }

    private static boolean isDup(CodeElement e) {
        return e instanceof StackInstruction s && s.opcode() == Opcode.DUP;
    }

    int emitSync(int monBlk, Set<Integer> stop, List<AstNode<StmtExpr>> out) {
        s.emittedSync.add(monBlk);
        BasicBlock mb = cfg.blocks.get(monBlk);
        int n = mb.instrs.size();
        int keep = (n >= 3 && isDup(cfg.seq.get(mb.instrs.get(n - 3)))) ? n - 3 : n - 1;
        int mark = body.stmts.size();
        List<CodeElement> ins = new ArrayList<>();
        List<Integer> idxs = new ArrayList<>();
        for (int i = 0; i < keep; i++) {
            ins.add(cfg.seq.get(mb.instrs.get(i)));
            idxs.add(mb.instrs.get(i));
        }
        body.translateSpan(ins, idxs, 0, ins.size());
        s.drainInto(out, mark);
        if (!body.stack.isEmpty()) {
            body.stack.pop();   // the lock object
        }

        int bodyStart = mb.fall;
        Trap guard = null;
        for (Trap e : cfg.excs) {
            if (e.startBlk() == bodyStart && e.type() == null) {
                guard = e;
                break;
            }
        }
        int follow;
        int handlerBlk = guard != null ? guard.handlerBlk() : -1;
        if (guard != null) {
            int endId = guard.endBlkId();
            follow = endId < cfg.blocks.size() ? s.chainExit(endId) : -1;
            s.markChainVisited(endId, follow);
            s.markHandlerVisited(handlerBlk);
            s.emittedTries.add(bodyStart);
            s.emittedTries.add(handlerBlk);
        } else {
            follow = -1;
        }
        Set<Integer> bodyStop = new HashSet<>(stop);
        if (handlerBlk >= 0) {
            bodyStop.add(handlerBlk);
        }
        if (follow >= 0) {
            bodyStop.add(follow);
        }
        s.region(bodyStart, bodyStop);   // structure the body first: a structural failure outranks the refusal
        // Laurel has no synchronized statement: refuse after the body
        // structures, with the corpus-pinned message.
        throw new JavaViolationException("Unsupported statement: SynchronizedStmt");
    }
}
