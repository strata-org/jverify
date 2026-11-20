package org.strata.java.bytecode.frontend;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Dominance/loop analysis over a {@link ControlFlowGraph}. */
public final class DominanceFinder {
    public final int n;
    public final BitSet[] dom;   // dom[b]  = blocks that dominate b (incl. b)
    public final int[] idom;     // immediate dominator (idom[entry] = entry)
    public final BitSet[] pdom;  // pdom[b] = blocks that post-dominate b (incl. b); index n = virtual exit
    public final int[] ipdom;    // immediate post-dominator (block id, or -1 == exit)
    public final Map<Integer,Set<Integer>> loops = new HashMap<>(); // header -> body block ids (incl. header)
    public final Map<Integer,Integer> loopTail = new HashMap<>();   // header -> the back-edge source (latch)
    /**
     * header -> the loop's own bytecode positions (its condition's tests and
     * supporting code, a for-each's hidden bookkeeping, a for's update), i.e.
     * the loop's pc extent minus every pc a user (sub-)statement covers; from
     * the CRT ({@code MethodBodyFactory.loopConditionPcs}). Absent without
     * {@code -Xjcov}.
     */
    public final Map<Integer,BitSet> loopCond = new HashMap<>();
    DominanceFinder(int n, BitSet[] dom, int[] idom, BitSet[] pdom, int[] ipdom) {
        this.n = n; this.dom = dom; this.idom = idom; this.pdom = pdom; this.ipdom = ipdom;
    }
    public boolean dominates(int a, int b) { return dom[b].get(a); }
    public boolean isLoopHeader(int b) { return loops.containsKey(b); }
}
