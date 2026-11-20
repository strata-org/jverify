package org.strata.java.bytecode.frontend;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.LabelTarget;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Control-flow graph over basic blocks for one method. */
public final class ControlFlowGraph {
    public final String owner, method;
    public final List<CodeElement> seq;      // instructions + label targets, in bytecode order
    public final List<Integer> instrIdx;     // seq-indices that are Instructions
    public final int[] nextInstr;            // for each seq position, the next instruction seq-index (or -1)
    public final Map<Label,Integer> labelPos;// label -> seq-index of its LabelTarget
    public final List<BasicBlock> blocks;
    public final Map<Integer,Integer> blockOf; // instruction seq-index -> block id
    public final List<Trap> excs;             // exception-table entries resolved to blocks (in table order)
    /**
     * Handler block -> the block where its catch clause ENDS, from the CRT. The exception table records
     * only where a handler begins, so without this the clause's extent has to be guessed from liveness.
     */
    public final Map<Integer,Integer> catchEnd = new HashMap<>();
    ControlFlowGraph(String owner, String method, List<CodeElement> seq, List<Integer> instrIdx, int[] nextInstr,
        Map<Label,Integer> labelPos, List<BasicBlock> blocks, Map<Integer,Integer> blockOf, List<Trap> excs) {
        this.owner = owner; this.method = method; this.seq = seq; this.instrIdx = instrIdx;
        this.nextInstr = nextInstr; this.labelPos = labelPos; this.blocks = blocks; this.blockOf = blockOf;
        this.excs = excs;
    }
}
