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
 * Emits Laurel loops from CFG loop regions: recovers do-while vs pre-tested
 * (while/for) shapes, consumes leading loop-contract markers (invariants),
 * re-stamps loop-body statement ranges, and applies JFE's emitLoop wrapping
 * (break/continue labels present iff a jump minted them).
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
        BasicBlock h = cfg.blocks.get(header);
        Set<Integer> bodyBlocks = dom.loops.get(header);
        int latch = dom.loopTail.get(header);
        BasicBlock lb = cfg.blocks.get(latch);

        // do-while: the latch carries the bottom test. Always labeled (the
        // driver's DoStmt arm minted both labels unconditionally).
        if (lb.isConditional() && (lb.branch == header || lb.fall == header)) {
            boolean backOnBranch = (lb.branch == header);
            int exit = backOnBranch ? lb.fall : lb.branch;
            s.visited[latch] = true;
            ControlFlowStructurer.Ctx c = new ControlFlowStructurer.Ctx(true, header, exit, latch, latch);
            s.ensureLabels(c);
            s.ctxStack.push(c);
            List<AstNode<StmtExpr>> bodyStmts = s.region(header, ControlFlowStructurer.plus(ControlFlowStructurer.plus(stop, latch), exit));
            AstNode<StmtExpr> cond = s.conditionOf(lb, bodyStmts);
            body.checkPlaceable(cond);
            if (backOnBranch) {
                cond = LaurelTrees.negate(cond, ctx.method.unordered);
            }
            s.ctxStack.pop();
            List<AstNode<StmtExpr>> invariants = extractLoopInvariants(bodyStmts);
            restampLoopBody(bodyStmts);
            out.add(s.place(buildDoWhile(cond, invariants, bodyStmts, c), latch));
            return exit;
        }

        // bottom-tested loop with the test in the HEADER -> do-while.
        if (h.isConditional() && bodyBlocks.contains(h.fall) != bodyBlocks.contains(h.branch)) {
            boolean backOnBranch = bodyBlocks.contains(h.branch);
            int inLoop = backOnBranch ? h.branch : h.fall;
            int exit = backOnBranch ? h.fall : h.branch;
            if (s.jumpsStraightBackTo(inLoop, header)) {
                s.markChainVisited(inLoop, header);
                List<AstNode<StmtExpr>> bodyStmts = new ArrayList<>();
                AstNode<StmtExpr> cond = s.conditionOf(h, bodyStmts);
                body.checkPlaceable(cond);
                if (backOnBranch) {
                    cond = LaurelTrees.negate(cond, ctx.method.unordered);
                }
                ControlFlowStructurer.Ctx c = new ControlFlowStructurer.Ctx(true, header, exit, latch, latch);
                s.ensureLabels(c);
                List<AstNode<StmtExpr>> invariants = extractLoopInvariants(bodyStmts);
                restampLoopBody(bodyStmts);
                out.add(s.place(buildDoWhile(cond, invariants, bodyStmts, c), header));
                return exit;
            }
        }

        // pre-tested while / for.
        if (h.isConditional() && bodyBlocks.contains(h.fall) != bodyBlocks.contains(h.branch)) {
            AstNode<StmtExpr> cond = s.conditionOf(h, out);
            body.checkPlaceable(cond);
            int bodyEntry, exit;
            if (bodyBlocks.contains(h.fall)) {
                bodyEntry = h.fall;
                exit = h.branch;
            } else {
                bodyEntry = h.branch;
                exit = h.fall;
                cond = LaurelTrees.negate(cond, ctx.method.unordered);
            }
            ControlFlowStructurer.Ctx c = new ControlFlowStructurer.Ctx(true, header, exit, latch, header);
            s.ctxStack.push(c);
            List<AstNode<StmtExpr>> bodyStmts = s.region(bodyEntry, ControlFlowStructurer.plus(ControlFlowStructurer.plus(stop, header), exit));
            s.ctxStack.pop();
            List<AstNode<StmtExpr>> invariants = extractLoopInvariants(bodyStmts);
            restampLoopBody(bodyStmts);
            out.add(s.place(buildPreTested(cond, invariants, bodyStmts, header, out, c), header));
            return exit;
        }

        // fallback: while(true){ body }.
        int exit = s.firstExit(bodyBlocks);
        ControlFlowStructurer.Ctx c = new ControlFlowStructurer.Ctx(true, header, exit, latch, header);
        s.ctxStack.push(c);
        s.loopEntryPass.add(header);
        s.infiniteHeaders.add(header);
        List<AstNode<StmtExpr>> bodyStmts;
        try {
            bodyStmts = s.region(header, ControlFlowStructurer.plus(ControlFlowStructurer.plus(stop, header), exit));
        } finally {
            s.loopEntryPass.remove(header);
            s.infiniteHeaders.remove(header);
            s.ctxStack.pop();
        }
        List<AstNode<StmtExpr>> invariants = extractLoopInvariants(bodyStmts);
        restampLoopBody(bodyStmts);
        AstNode<StmtExpr> whileNode = body.e(new StmtExpr.While(
                body.e(new StmtExpr.LiteralBool(true)), invariants, Optional.empty(),
                s.blockOf(bodyStmts), false));
        out.add(s.place(wrapBreak(whileNode, c), header));
        return exit;
    }

    /**
     * Header scan over the emitted loop body (JFE's extractLoopParts):
     * leading spec markers are consumed — invariants convert (at their
     * argument range, contract-checked), reads/modifies/decreases drop, and
     * pre/postconditions refuse. The markers are removed from the body.
     */
    private List<AstNode<StmtExpr>> extractLoopInvariants(List<AstNode<StmtExpr>> bodyStmts) {
        var invariants = new ArrayList<AstNode<StmtExpr>>();
        int i = 0;
        for (; i < bodyStmts.size(); i++) {
            var spec = body.specStmts.get(bodyStmts.get(i));
            if (spec == null) {
                break;
            }
            // Consumed as a loop-contract clause: it is no longer a method
            // spec marker (the driver's refusal passes must not see it).
            body.specStmts.remove(bodyStmts.get(i));
            switch (spec.name()) {
                case "precondition", "postcondition" ->
                        throw new JavaViolationException("preconditions are not allowed in a loop");
                case "invariant" -> {
                    if (spec.args().size() != 1) {
                        throw new JavaViolationException(
                                "A call to 'invariant' may have only one argument");
                    }
                    FileRange argRange = ctx.src.argumentRange(bodyStmts.get(i).source());
                    AstNode<StmtExpr> arg;
                    if (spec.lambdaArg() != null) {
                        arg = body.translateLambdaBody(spec.lambdaArg(), Map.of());
                    } else {
                        arg = spec.args().get(0);
                        body.checkContractArg(arg);
                    }
                    arg = body.coerce(arg, "Z");
                    AstNode<StmtExpr> inv = LaurelTrees.rerange(arg, argRange, ctx.method.statementNodes);
                    // Pinned: the loop's own placement must not restamp the
                    // invariant's argument-range subtree.
                    ctx.method.statementNodes.add(inv);
                    invariants.add(inv);
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
            bodyStmts.subList(0, i).clear();
        }
        return invariants;
    }

    /**
     * Loop-body statements carry the FULL statement range, semicolon
     * included: re-stamp the top-level
     * statements that were placed with the stripped narrowing. Identity keys
     * of rebuilt call nodes are remapped so the driver's heap-write walk
     * still resolves them.
     */
    private void restampLoopBody(List<AstNode<StmtExpr>> bodyStmts) {
        for (int i = 0; i < bodyStmts.size(); i++) {
            AstNode<StmtExpr> s = bodyStmts.get(i);
            if (body.specStmts.containsKey(s)) {
                continue;   // markers keep identity (mid-body refusal)
            }
            Integer at = body.stripSemiAt.get(s);
            if (at == null) {
                continue;
            }
            FileRange full = body.stmtRange(at, BodyTranslator.Narrow.RAW, false);
            ctx.method.statementNodes.remove(s);
            AstNode<StmtExpr> re = LaurelTrees.rerange(s, full, ctx.method.statementNodes);
            ctx.method.statementNodes.add(re);
            body.remapIdentity(s, re);
            bodyStmts.set(i, re);
        }
    }

    /** do-while as the driver encoded it: while(true){ {body}:cont; if(!cond) exit(break); } wrapped :break. */
    private AstNode<StmtExpr> buildDoWhile(AstNode<StmtExpr> cond, List<AstNode<StmtExpr>> invariants,
                                           List<AstNode<StmtExpr>> bodyStmts, ControlFlowStructurer.Ctx c) {
        AstNode<StmtExpr> wrappedBody = body.e(new StmtExpr.Block(
                List.of(s.blockOf(bodyStmts)), Optional.of(c.continueLabel)));
        // The driver's DoStmt arm wrapped `!(cond)` VERBATIM (UnaryExpr
        // conversion -> $not), never flipping the comparison.
        AstNode<StmtExpr> notCond = body.e(LaurelFactory.primitiveOp(new Operation.Not(), cond));
        AstNode<StmtExpr> exitIfDone = body.e(new StmtExpr.IfThenElse(notCond,
                s.exit(c.breakLabel), Optional.empty()));
        AstNode<StmtExpr> whileBody = body.e(new StmtExpr.Block(
                List.of(wrappedBody, exitIfDone), Optional.empty()));
        AstNode<StmtExpr> whileNode = body.e(new StmtExpr.While(
                body.e(new StmtExpr.LiteralBool(true)), invariants, Optional.empty(), whileBody, false));
        return body.e(new StmtExpr.Block(List.of(whileNode), Optional.of(c.breakLabel)));
    }

    /**
     * Pre-tested loop, recovering {@code for} vs {@code while} from the CRT
     * (step on the header's source line -> a {@code for} update clause).
     * JFE's emitLoop wrapping (break/continue labels, step placement) is
     * applied via {@link #wrapPreTested}.
     */
    private AstNode<StmtExpr> buildPreTested(AstNode<StmtExpr> cond, List<AstNode<StmtExpr>> invariants,
                                             List<AstNode<StmtExpr>> bodyStmts,
                                             int header, List<AstNode<StmtExpr>> out, ControlFlowStructurer.Ctx c) {
        Span hs = s.headerSpan(header);
        if (!hs.isNone() && !bodyStmts.isEmpty()) {
            AstNode<StmtExpr> last = bodyStmts.get(bodyStmts.size() - 1);
            Span ls = s.spanOfNode(last);
            if (ls != null && !ls.isNone() && ls.beginLine() == hs.beginLine()
                    && bodyStmts.size() > 1 && startsAfter(bodyStmts, hs)) {
                List<AstNode<StmtExpr>> inner = new ArrayList<>(bodyStmts.subList(0, bodyStmts.size() - 1));
                AstNode<StmtExpr> step = last;
                AstNode<StmtExpr> init = initClause(hs, out);
                List<AstNode<StmtExpr>> preamble = init == null ? List.of() : List.of(init);
                return wrapPreTested(cond, invariants, inner, step, preamble, c);
            }
        }
        return wrapPreTested(cond, invariants, bodyStmts, null, List.of(), c);
    }

    /** JFE's emitLoop wrapping, with labels present iff this loop minted them. */
    private AstNode<StmtExpr> wrapPreTested(AstNode<StmtExpr> cond, List<AstNode<StmtExpr>> invariants,
                                            List<AstNode<StmtExpr>> bodyStmts,
                                            AstNode<StmtExpr> step, List<AstNode<StmtExpr>> preamble, ControlFlowStructurer.Ctx c) {
        if (c.breakLabel != null) {
            AstNode<StmtExpr> wrappedBody = body.e(new StmtExpr.Block(
                    List.of(s.blockOf(bodyStmts)), Optional.of(c.continueLabel)));
            AstNode<StmtExpr> whileBody = step != null
                    ? body.e(new StmtExpr.Block(List.of(wrappedBody, step), Optional.empty()))
                    : wrappedBody;
            AstNode<StmtExpr> whileNode = body.e(new StmtExpr.While(cond, invariants,
                    Optional.empty(), whileBody, false));
            List<AstNode<StmtExpr>> outer = new ArrayList<>(preamble);
            outer.add(whileNode);
            return body.e(new StmtExpr.Block(outer, Optional.of(c.breakLabel)));
        }
        AstNode<StmtExpr> whileBody = step != null
                ? body.e(new StmtExpr.Block(List.of(s.blockOf(bodyStmts), step), Optional.empty()))
                : s.blockOf(bodyStmts);
        AstNode<StmtExpr> whileNode = body.e(new StmtExpr.While(cond, invariants,
                Optional.empty(), whileBody, false));
        if (preamble.isEmpty()) {
            return whileNode;
        }
        List<AstNode<StmtExpr>> stmts = new ArrayList<>(preamble);
        stmts.add(whileNode);
        return body.e(new StmtExpr.Block(stmts, Optional.empty()));
    }

    /** while(true) fallback wrapping: break label only if minted. */
    private AstNode<StmtExpr> wrapBreak(AstNode<StmtExpr> whileNode, ControlFlowStructurer.Ctx c) {
        if (c.breakLabel == null) {
            return whileNode;
        }
        return body.e(new StmtExpr.Block(List.of(whileNode), Optional.of(c.breakLabel)));
    }

    /** Reclaim a for's init clause from the preceding emitted statement (same source line). */
    private AstNode<StmtExpr> initClause(Span hs, List<AstNode<StmtExpr>> out) {
        if (out.isEmpty()) {
            return null;
        }
        AstNode<StmtExpr> prev = out.get(out.size() - 1);
        Span ps = s.spanOfNode(prev);
        if (ps == null || ps.isNone() || ps.beginLine() != hs.beginLine()) {
            return null;
        }
        out.remove(out.size() - 1);
        return prev;
    }

    boolean startsAfter(List<AstNode<StmtExpr>> bodyStmts, Span header) {
        for (int i = 0; i < bodyStmts.size() - 1; i++) {
            Span sp = s.spanOfNode(bodyStmts.get(i));
            if (sp != null && !sp.isNone() && sp.beginLine() <= header.beginLine()) {
                return false;
            }
        }
        return true;
    }
}
