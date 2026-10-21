package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Invariant;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER, Blocker.STRATA_OTHER}, exitCode = 4)
class AmortizedQueueInvalid {
    private final int[] front;
    private final int[] back;
    private int frontSize;
    private int backSize;

    AmortizedQueueInvalid(int capacity) {
        precondition(capacity > 0);
        this.front = new int[capacity];
        this.back = new int[capacity];
        this.frontSize = 0;
        this.backSize = 0;
    }

    @Pure
    @Invariant
    private boolean representation() {
        reads(this);
        return front.length == back.length
                && 0 <= frontSize && frontSize <= front.length
                && 0 <= backSize && backSize <= back.length
                && frontSize + backSize <= front.length;
    }

    @Pure
    public int length() {
        reads(this);
        return frontSize + backSize;
    }

    @Pure
    public int capacity() {
        reads(this);
        return front.length;
    }

    @Pure
    public int elementAt(int k) {
        reads(this);
        reads(front);
        reads(back);
        precondition(0 <= k && k < frontSize + backSize);
        return k < frontSize ? front[frontSize - 1 - k] : back[k - frontSize];
    }

    public void enqueue(int v) {
        precondition(length() < capacity());
        modifies(this);
        modifies(back);
        postcondition(length() == old(length()) + 1);
        postcondition(elementAt(length() - 1) == v);
        postcondition(forall((int k) -> implies(0 <= k && k < old(length()), elementAt(k) == old(elementAt(k)))));

        back[backSize] = v;
        backSize = backSize + 1;
    }

    public int dequeue() {
        precondition(length() > 0);
        modifies(this);
        modifies(front);
        modifies(back);
        postcondition((int res) -> res == old(elementAt(0)));
//                                 ^^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved
        postcondition(length() == old(length()) - 1);
        postcondition(forall((int k) -> implies(0 <= k && k < length(), elementAt(k) == old(elementAt(k + 1)))));
//                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved

        if (frontSize == 0) {
            int i = 0;
            while (i < backSize) {
                modifies(front);
                invariant(0 <= i && i <= backSize);
                front[i] = back[i];
                i = i + 1;
            }
            frontSize = backSize;
            backSize = 0;
        }
        frontSize = frontSize - 1;
        return front[frontSize];
    }
}
