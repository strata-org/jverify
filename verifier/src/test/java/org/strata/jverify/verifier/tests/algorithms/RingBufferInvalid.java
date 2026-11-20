package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Invariant;
import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER, Blocker.STRATA_OTHER}, exitCode = 4)
class RingBufferInvalid {
    private final int[] buf;
    private int head;
    private int count;

    RingBufferInvalid(int capacity) {
        precondition(0 < capacity && capacity <= 1000000);
        this.buf = new int[capacity];
        this.head = 0;
        this.count = 0;
    }

    @Transparent
    @Invariant
    private boolean inRange() {
        reads(this);
        return 0 < buf.length && buf.length <= 1000000
                && 0 <= head && head < buf.length && 0 <= count && count <= buf.length;
    }

    @Transparent
    public int length() {
        reads(this);
        return count;
    }

    @Transparent
    public int capacity() {
        reads(this);
        return buf.length;
    }

    @Transparent
    public int elementAt(int k) {
        reads(this);
        reads(buf);
        precondition(0 <= k && k < count);
        return buf[(head + k) % buf.length];
    }

    public void push(int v) {
        precondition(length() < capacity());
        modifies(this);
        modifies(buf);
        postcondition(length() == old(length()) + 1);
        postcondition(elementAt(length() - 1) == v);
        postcondition(forall((int k) -> implies(0 <= k && k < old(length()), elementAt(k) == old(elementAt(k)))));

        buf[(head + count) % buf.length] = v;
        count = count + 1;
    }

    public int pop() {
//  ^^^^^^^^^^^^^^^^ Error: postcondition could not be proved
        precondition(length() > 0);
        modifies(this);
        postcondition((int res) -> res == old(elementAt(0)));
        postcondition(length() == old(length()) - 1);
        postcondition(forall((int k) -> implies(0 <= k && k < length(), elementAt(k) == old(elementAt(k + 1)))));

        int v = buf[head];
        head = head + 1;
        count = count - 1;
        return v;
    }
}
