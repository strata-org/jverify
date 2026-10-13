package org.strata.java.bytecode.verifier.compiler;

import org.strata.java.bytecode.frontend.MethodBody;

import java.util.List;

/**
 * Throw-coverage prescan over bytecode: every ATHROW whose thrown static
 * type is recoverable from the preceding instruction (allocation, cast, or
 * typed local) is coverage-checked BEFORE any translation, so a conversion
 * refusal earlier in the code cannot mask the declared-throws diagnostic.
 * Local catch scoping comes from the exception table. Unrecognized shapes
 * are left to the inline ATHROW check.
 */
final class ThrowCoveragePrescan {

    private ThrowCoveragePrescan() {
    }

    static void check(MethodBody shape, TranslationContext ctx) {
        if (ctx.method.info.quiet) {
            return;
        }
        var seq = shape.seq();
        var cfg = shape.cfg();
        for (int i = 0; i < seq.size(); i++) {
            if (!(seq.get(i) instanceof java.lang.classfile.Instruction in)
                    || in.opcode() != java.lang.classfile.Opcode.ATHROW) {
                continue;
            }
            int j = i - 1;
            while (j >= 0 && !(seq.get(j) instanceof java.lang.classfile.Instruction)) {
                j--;
            }
            String thrownFqn = null;
            if (j >= 0) {
                switch (seq.get(j)) {
                    case java.lang.classfile.instruction.TypeCheckInstruction tc
                            when tc.opcode() == java.lang.classfile.Opcode.CHECKCAST ->
                            thrownFqn = JavaNames.dottedFqn(tc.type().asInternalName());
                    case java.lang.classfile.instruction.InvokeInstruction inv
                            when inv.name().stringValue().equals("<init>") ->
                            thrownFqn = JavaNames.dottedFqn(inv.owner().asInternalName());
                    case java.lang.classfile.instruction.LoadInstruction li -> {
                        var sc = shape.locals() != null ? shape.locals().at(li.slot(), j) : null;
                        String desc = sc != null ? sc.desc() : shape.slotDesc().get(li.slot());
                        if (desc != null && desc.startsWith("L")) {
                            thrownFqn = JavaNames.dottedFqn(desc.substring(1, desc.length() - 1));
                        } else if (desc == null && cfg != null) {
                            // A synthetic (LVT-less) temp rethrown from a
                            // TYPED handler: try-with-resources' catch
                            // (Throwable t) { ...; throw t; } — a real throw
                            // for coverage purposes. Catch-ALL handlers
                            // (type null) are the finally/synchronized
                            // machinery, which the structurer consumes:
                            // never a source throw.
                            for (var e : cfg.excs) {
                                if (e.type() == null) {
                                    continue;
                                }
                                var hb = cfg.blocks.get(e.handlerBlk());
                                if (!hb.instrs.isEmpty()
                                        && cfg.seq.get(hb.instrs.get(0))
                                                instanceof java.lang.classfile.instruction.StoreInstruction hs
                                        && hs.slot() == li.slot()) {
                                    thrownFqn = TypeRef.ofClassDesc(e.type()).fqnFromContext();
                                    break;
                                }
                            }
                        }
                    }
                    default -> {
                    }
                }
            }
            if (thrownFqn == null || !ctx.resolver.isThrowableSubtype(thrownFqn)) {
                continue;
            }
            // Contract code: `throw new ContractException()` says the member
            // has no implementation; it is not a throw.
            if (ctx.contractMode && thrownFqn.equals("org.strata.jverify.ContractException")) {
                continue;
            }
            // A `throw new AssertionError` guarded by $assertionsDisabled is
            // the assert-statement desugar: the structurer recovers it as an
            // AssertStmt: not a throw for coverage purposes.
            if (thrownFqn.equals("java.lang.AssertionError") && usesAssertionsDisabled(seq, i)) {
                continue;
            }
            boolean caught = false;
            if (cfg != null) {
                Integer blk = cfg.blockOf.get(i);
                if (blk != null) {
                    for (var e : cfg.excs) {
                        if (e.type() == null || blk < e.startBlk() || blk >= e.endBlkId()) {
                            continue;
                        }
                        String caughtFqn = TypeRef.ofClassDesc(e.type()).fqnFromContext();
                        if (caughtFqn != null && ctx.resolver.isSubclassOf(thrownFqn, caughtFqn)) {
                            caught = true;
                            break;
                        }
                    }
                }
            }
            if (caught || ctx.method.throwsJoin == null) {
                // No throws clause: an uncovered throw is asserted unreachable
                // by Laurel rather than refused (see checkThrowCoverage).
                continue;
            }
            if (!ctx.resolver.isSubclassOf(thrownFqn, ctx.method.throwsJoin)) {
                throw new JavaViolationException(
                        "thrown type '" + thrownFqn
                                + "' is not covered by the declared throws clause ('"
                                + ctx.method.throwsJoin + "')");
            }
        }
    }

    /**
     * A typed handler whose protected range contains no throw and no call cannot be
     * entered: nothing in it can produce an exception. Reported at the handler's
     * line, and the method demoted.
     */
    static void checkDeadHandlers(MethodBody shape, TranslationContext ctx) {
        var cfg = shape.cfg();
        if (cfg == null || shape.lines() == null) {
            return;
        }
        for (var e : cfg.excs) {
            if (e.type() == null) {
                continue;
            }
            if (rangeCanThrow(shape, e.startBlk(), e.endBlkId())) {
                continue;
            }
            var handler = cfg.blocks.get(e.handlerBlk());
            if (handler.instrs.isEmpty()) {
                continue;
            }
            int line = shape.lines().lineAt(shape.seqToBci()[handler.instrs.get(0)]);
            String caught = TypeRef.ofClassDesc(e.type()).fqnFromContext();
            if (!ctx.method.info.quiet) {
                ctx.reporter.reportError(ctx.src.uri, ctx.src.positionRange(ctx.src.lineRange(line)),
                        "no reachable throw can produce '" + caught + "'; this handler is dead code");
            }
            ctx.method.info.constructErrorReported = true;
        }
    }

    private static boolean rangeCanThrow(MethodBody shape, int startBlk, int endBlk) {
        var cfg = shape.cfg();
        for (int b = startBlk; b < endBlk; b++) {
            for (int idx : cfg.blocks.get(b).instrs) {
                if (!(shape.seq().get(idx) instanceof java.lang.classfile.Instruction in)) {
                    continue;
                }
                if (mayThrow(in)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean mayThrow(java.lang.classfile.Instruction in) {
        if (in instanceof java.lang.classfile.instruction.InvokeInstruction
                || in instanceof java.lang.classfile.instruction.InvokeDynamicInstruction
                || in instanceof java.lang.classfile.instruction.FieldInstruction
                || in instanceof java.lang.classfile.instruction.ArrayLoadInstruction
                || in instanceof java.lang.classfile.instruction.ArrayStoreInstruction
                || in instanceof java.lang.classfile.instruction.NewObjectInstruction
                || in instanceof java.lang.classfile.instruction.NewPrimitiveArrayInstruction
                || in instanceof java.lang.classfile.instruction.NewReferenceArrayInstruction
                || in instanceof java.lang.classfile.instruction.NewMultiArrayInstruction
                || in instanceof java.lang.classfile.instruction.TypeCheckInstruction
                || in instanceof java.lang.classfile.instruction.MonitorInstruction) {
            return true;
        }
        return switch (in.opcode()) {
            case ATHROW, IDIV, IREM, LDIV, LREM, ARRAYLENGTH -> true;
            default -> false;
        };
    }

    /** True if a $assertionsDisabled read precedes {@code before} in the code sequence. */
    private static boolean usesAssertionsDisabled(
            List<java.lang.classfile.CodeElement> seq, int before) {
        for (int i = 0; i < before; i++) {
            if (seq.get(i) instanceof java.lang.classfile.instruction.FieldInstruction fi
                    && fi.opcode() == java.lang.classfile.Opcode.GETSTATIC
                    && fi.name().stringValue().equals("$assertionsDisabled")) {
                return true;
            }
        }
        return false;
    }

}
