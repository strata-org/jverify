package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Invariant;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER, Blocker.STRATA_OTHER}, exitCode = 4)
class BoundedStackInvalid {
    private final int[] items;
    private int size;

    BoundedStackInvalid(int capacity) {
        precondition(capacity > 0);
        postcondition(this.size == 0);
        this.items = new int[capacity];
        this.size = 0;
    }

    @Pure
    @Invariant
    private boolean sizeInRange() {
        reads(this);
        return 0 <= size && size <= items.length;
    }

    @Pure
    public int size() {
        reads(this);
        return size;
    }

    @Pure
    public int capacity() {
        reads(this);
        return items.length;
    }

    @Pure
    public int peekAt(int k) {
        reads(this);
        reads(items);
        precondition(0 <= k && k < size);
        return items[k];
    }

    public void push(int v) {
        precondition(size() < capacity());
        modifies(this);
        modifies(items);
        postcondition(size() == old(size()) + 1);
        postcondition(peekAt(size() - 1) == v);
//                    ^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved
        postcondition(forall((int k) -> implies(0 <= k && k < old(size()), peekAt(k) == old(peekAt(k)))));
//                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved

        items[size - 1] = v;
//      ^^^^^^^^^^^^^^^^^^^^ Error: precondition does not hold
        size = size + 1;
    }

    public int pop() {
        precondition(size() > 0);
        modifies(this);
        postcondition((int res) -> res == old(peekAt(size() - 1)));
        postcondition(size() == old(size()) - 1);

        size = size - 1;
        return items[size];
    }
}
