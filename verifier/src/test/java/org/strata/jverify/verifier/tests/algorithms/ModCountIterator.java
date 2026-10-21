package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Invariant;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER}, exitCode = 0)
class ModCountIterator {
    static class IntList {
        final int[] data;
        int size;
        int modCount;

        IntList(int capacity) {
            precondition(capacity > 0);
            this.data = new int[capacity];
            this.size = 0;
            this.modCount = 0;
        }

        @Pure
        @Invariant
        private boolean inRange() {
            reads(this);
            return 0 <= size && size <= data.length && 0 <= modCount && modCount < 2147483647;
        }

        public void add(int v) {
            precondition(size < data.length && modCount < 2147483646);
            modifies(this);
            modifies(data);
            postcondition(size == old(size) + 1 && modCount == old(modCount) + 1);

            data[size] = v;
            size = size + 1;
            modCount = modCount + 1;
        }
    }

    static class Iter {
        IntList list;
        int cursor;
        int expectedModCount;

        Iter(IntList list) {
            postcondition(this.list == list && this.cursor == 0 && this.expectedModCount == list.modCount);
            this.list = list;
            this.cursor = 0;
            this.expectedModCount = list.modCount;
        }

        @Pure
        boolean unmodified() {
            reads(this);
            reads(list);
            return list.modCount == expectedModCount;
        }

        @Pure
        boolean hasNext() {
            reads(this);
            reads(list);
            return cursor < list.size;
        }

        int next() {
            precondition(unmodified());
            precondition(hasNext());
            precondition(0 <= cursor);
            modifies(this);
            postcondition((int res) -> res == list.data[old(cursor)] && cursor == old(cursor) + 1);

            int v = list.data[cursor];
            cursor = cursor + 1;
            return v;
        }
    }

    @Pure
    @Erased
    static @Unbounded int sumBelow(int[] a, @Unbounded int hi) {
        reads(a);
        decreases(hi);
        if (hi <= 0) {
            return 0;
        }
        return sumBelow(a, hi - 1) + a[hi - 1];
    }

    static @Unbounded int total(IntList list) {
        postcondition((int res) -> res == sumBelow(list.data, list.size));

        Iter it = new Iter(list);
        @Unbounded int s = 0;
        while (it.hasNext()) {
            modifies(it);
            invariant(it.list == list && it.unmodified());
            invariant(0 <= it.cursor && it.cursor <= list.size);
            invariant(s == sumBelow(list.data, it.cursor));

            s = s + it.next();
        }
        return s;
    }
}
