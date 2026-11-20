package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Invariant;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER, Blocker.STRATA_OTHER}, exitCode = 4)
class SlabInvalid {
    private final int[] values;
    private final boolean[] occupied;
    private final int[] nextFree;
    private int freeHead;
    private int len;

    SlabInvalid(int capacity) {
        precondition(capacity > 0);
        this.values = new int[capacity];
        this.occupied = new boolean[capacity];
        this.nextFree = new int[capacity];
        this.freeHead = -1;
        this.len = 0;
    }

    @Transparent
    @Erased
    static boolean freeChainFrom(boolean[] occupied, int[] nextFree, int at, @Unbounded int steps) {
        reads(occupied);
        reads(nextFree);
        decreases(steps);
        if (at == -1) {
            return true;
        }
        return steps > 0 && 0 <= at && at < occupied.length && !occupied[at]
                && freeChainFrom(occupied, nextFree, nextFree[at], steps - 1);
    }

    @Transparent
    @Invariant
    private boolean wellFormed() {
        reads(this);
        reads(occupied);
        reads(nextFree);
        return values.length == occupied.length && occupied.length == nextFree.length
                && 0 <= len && len <= values.length
                && freeChainFrom(occupied, nextFree, freeHead, values.length);
    }

    @Transparent
    public boolean contains(int key) {
        reads(this);
        reads(occupied);
        return 0 <= key && key < len && occupied[key];
    }

    @Transparent
    public int get(int key) {
        reads(this);
        reads(values);
        precondition(contains(key));
        return values[key];
    }

    public int insert(int v) {
        precondition(freeHead != -1 || len < values.length);
        modifies(this);
        modifies(values);
        modifies(occupied);
        modifies(nextFree);
        postcondition((int key) -> contains(key) && get(key) == v);
        postcondition((int key) -> !old(contains(key)));
        postcondition((int key) -> forall((int k) -> implies(old(contains(k)), contains(k) && get(k) == old(get(k)))));

        int key;
        if (freeHead != -1) {
            key = freeHead;
            freeHead = nextFree[key];
        } else {
            key = len;
            len = len + 1;
        }
        values[key] = v;
        occupied[key] = true;
        return key;
    }

    public int remove(int key) {
        precondition(contains(key));
        modifies(this);
        modifies(occupied);
        modifies(nextFree);
        postcondition((int res) -> res == old(get(key)));
        postcondition(!contains(key));
//                    ^^^^^^^^^^^^^^ Error: postcondition does not hold
        postcondition(forall((int k) -> implies(k != key && old(contains(k)), contains(k) && get(k) == old(get(k)))));

        int v = values[key];
        nextFree[key] = freeHead;
        freeHead = key;
        return v;
    }
}
