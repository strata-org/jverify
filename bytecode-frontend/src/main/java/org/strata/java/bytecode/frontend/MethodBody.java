package org.strata.java.bytecode.frontend;

import java.lang.classfile.CodeElement;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The per-method ingredients the bytecode->Laurel translator needs: the CFG + dominators, the
 * scoped local table, the instruction sequence, a parameter-and-hoist-seeded {@code declared} set,
 * CRT spans + the seq-index->pc bridge, per-slot type annotations, the return descriptor, the
 * ctor/static flags, and the hoisted local declarations. {@code cfg}/{@code dom} are null for a
 * single-block (straight-line) method, and the whole body is null when the method has no Code.
 */
public record MethodBody(
        ControlFlowGraph cfg, DominanceFinder dom, Locals locals, List<CodeElement> seq,
        Map<Integer, String> slotName, Map<Integer, String> slotDesc, Set<String> declared,
        List<CrtRange> crt, LineNumbers lines, int[] seqToBci,
        Map<Integer, List<java.lang.classfile.Annotation>> slotAnnos,
        String retDesc, boolean isCtor, boolean isStatic, List<HoistedDecl> hoisted) {

    /**
     * One local declaration hoisted to the method top. For a debug-info-less input (no LVT),
     * {@code desc} is inferred from the first store opcode's type kind.
     */
    public record HoistedDecl(int slot, int seqIdx, String name, String desc, String genericType) { }
}
