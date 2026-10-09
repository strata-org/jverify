package org.strata.java.bytecode.frontend;

import java.lang.classfile.CodeElement;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The per-method ingredients the bytecode->Laurel translator needs: the CFG + dominators, the
 * scoped local table, the instruction sequence, a parameter-and-hoist-seeded {@code declared} set,
 * CRT spans + the seq-index->pc bridge, per-slot type annotations, the return descriptor, the
 * ctor/static flags, the hoisted local declarations, and whether the code has locals with no debug
 * information. {@code cfg}/{@code dom} are null for a single-block (straight-line) method, and the
 * whole body is null when the method has no Code.
 *
 * @param slotAnnos per-slot type annotations other than {@code @Impure}, from every local and
 *        parameter that occupies the slot
 * @param impureLocals the locals and parameters declared {@code @Impure}, each with the bytecode
 *        range where it occupies its slot (javac reuses a slot once a block-scoped local dies)
 * @param hasLocalsWithoutDebugInfo the code has no LocalVariableTable and {@code maxLocals > (static ?
 *        0 : 1)}: it was compiled without {@code -g}, and some parameter or local has no recorded
 *        name or type
 */
public record MethodBody(
        ControlFlowGraph cfg, DominanceFinder dom, Locals locals, List<CodeElement> seq,
        Map<Integer, String> slotName, Map<Integer, String> slotDesc, Set<String> declared,
        List<CrtRange> crt, LineNumbers lines, int[] seqToBci,
        Map<Integer, List<java.lang.classfile.Annotation>> slotAnnos,
        List<SlotRange> impureLocals,
        String retDesc, boolean isCtor, boolean isStatic, List<HoistedDecl> hoisted,
        boolean hasLocalsWithoutDebugInfo) {

    /** A slot occupied by one local over the bytecode range {@code [startBci, endBci)}. */
    public record SlotRange(int slot, int startBci, int endBci) {
        public boolean covers(int slot, int bci) {
            return this.slot == slot && startBci <= bci && bci < endBci;
        }
    }

    /**
     * One local declaration hoisted to the method top. For a debug-info-less input (no LVT),
     * {@code desc} is inferred from the first store opcode's type kind.
     */
    public record HoistedDecl(int slot, int seqIdx, String name, String desc, String genericType) { }
}
