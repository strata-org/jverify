package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Invariant;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER}, exitCode = 4)
class BagInvalid {
    private final int[] items;
    private int size;

    BagInvalid(int capacity) {
        precondition(capacity > 0);
        this.items = new int[capacity];
        this.size = 0;
    }

    @Transparent
    @Invariant
    private boolean sizeInRange() {
        reads(this);
        return 0 <= size && size <= items.length;
    }

    @Transparent
    @Erased
    static @Unbounded int countBelow(int[] a, int v, @Unbounded int hi) {
        reads(a);
        decreases(hi);
        if (hi <= 0) {
            return 0;
        }
        return countBelow(a, v, hi - 1) + (a[hi - 1] == v ? 1 : 0);
    }

    @Transparent
    public @Unbounded int count(int v) {
        reads(this);
        reads(items);
        return countBelow(items, v, size);
    }

    @Transparent
    public int size() {
        reads(this);
        return size;
    }

    @Transparent
    public int capacity() {
        reads(this);
        return items.length;
    }

    public void add(int v) {
        precondition(size() < capacity());
        modifies(this);
        modifies(items);
        postcondition(forall((int w) -> count(w) == old(count(w)) + (w == v ? 1 : 0)));

        items[size] = v;
        size = size + 1;
    }

    public boolean remove(int v) {
        modifies(this);
        modifies(items);
        postcondition((boolean res) -> res == (old(count(v)) > 0));
        postcondition(forall((int w) -> count(w) == old(count(w)) - (w == v && old(count(v)) > 0 ? 1 : 0)));
//                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved

        int i = 0;
        while (i < size) {
            invariant(0 <= i && i <= size);
            invariant(countBelow(items, v, i) == 0);

            if (items[i] == v) {
                items[i] = items[0];
                size = size - 1;
                return true;
            }
            i = i + 1;
        }
        return false;
    }
}
