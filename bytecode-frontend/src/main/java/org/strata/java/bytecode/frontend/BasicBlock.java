package org.strata.java.bytecode.frontend;

import java.lang.classfile.Instruction;
import java.util.ArrayList;
import java.util.List;

/** A basic block: a maximal run of instructions with a single entry and single exit. */
public final class BasicBlock {
    public final int id;
    public final List<Integer> instrs = new ArrayList<>();  // seq-indices of the instructions in this block
    public Instruction last;                                // last (terminator) instruction of the block
    public int fall = -1;      // fallthrough successor block id (conditional-branch not-taken / normal), or -1
    public int branch = -1;    // conditional-branch taken target block id, or -1
    public int gotoTarget = -1;// unconditional GOTO target block id, or -1
    public int defaultCase = -1;               // switch default target block id, or -1
    public final List<int[]> cases = new ArrayList<>(); // switch {caseValue, blockId}
    public final List<Integer> succ = new ArrayList<>();  // all successor block ids (dedup, for graph algorithms)
    public final List<Integer> preds = new ArrayList<>();
    BasicBlock(int id) { this.id = id; }
    public boolean isConditional() { return branch >= 0 && fall >= 0; }
    public boolean isSwitch()      { return defaultCase >= 0 || !cases.isEmpty(); }
    public boolean isReturnOrThrow() { return succ.isEmpty(); }
}
