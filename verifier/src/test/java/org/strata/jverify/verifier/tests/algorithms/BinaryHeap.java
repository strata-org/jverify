package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Invariant;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR, Blocker.FRONTEND_OTHER}, exitCode = 0)
class BinaryHeap {
    private final int[] heap;
    private int size;

    BinaryHeap(int capacity) {
        precondition(0 < capacity && capacity <= 1000000);
        this.heap = new int[capacity];
        this.size = 0;
    }

    @Pure
    @Erased
    static boolean heapExcept(int[] a, int size, int hole) {
        reads(a);
        return forall((int k) -> implies(0 < k && k < size && k != hole, a[(k - 1) / 2] >= a[k]));
    }

    @Pure
    @Erased
    static boolean heapExceptBelow(int[] a, int size, int hole) {
        reads(a);
        return forall((int k) -> implies(0 < k && k < size && (k - 1) / 2 != hole, a[(k - 1) / 2] >= a[k]));
    }

    @Pure
    @Erased
    static @Unbounded int countBelow(int[] a, int v, @Unbounded int hi) {
        reads(a);
        decreases(hi);
        if (hi <= 0) {
            return 0;
        }
        return countBelow(a, v, hi - 1) + (a[hi - 1] == v ? 1 : 0);
    }

    @Pure
    @Invariant
    private boolean isHeap() {
        reads(this);
        reads(heap);
        return 0 <= size && size <= heap.length && heap.length <= 1000000 && heapExcept(heap, size, -1);
    }

    @Pure
    public int size() {
        reads(this);
        return size;
    }

    @Pure
    public int capacity() {
        reads(this);
        return heap.length;
    }

    @Pure
    public @Unbounded int count(int v) {
        reads(this);
        reads(heap);
        return countBelow(heap, v, size);
    }

    @Pure
    public int max() {
        reads(this);
        reads(heap);
        precondition(size > 0);
        return heap[0];
    }

    public void insert(int v) {
        precondition(size() < capacity());
        modifies(this);
        modifies(heap);
        postcondition(size() == old(size()) + 1);
        postcondition(forall((int w) -> count(w) == old(count(w)) + (w == v ? 1 : 0)));

        int i = size;
        heap[i] = v;
        size = size + 1;
        while (i > 0 && heap[(i - 1) / 2] < heap[i]) {
            modifies(heap);
            invariant(0 <= i && i < size);
            invariant(heapExcept(heap, size, i));
            invariant(i <= 0 || 2 * i + 1 >= size || heap[(i - 1) / 2] >= heap[2 * i + 1]);
            invariant(i <= 0 || 2 * i + 2 >= size || heap[(i - 1) / 2] >= heap[2 * i + 2]);

            int parent = (i - 1) / 2;
            int tmp = heap[parent];
            heap[parent] = heap[i];
            heap[i] = tmp;
            i = parent;
        }
    }

    public int extractMax() {
        precondition(size() > 0);
        modifies(this);
        modifies(heap);
        postcondition((int res) -> res == old(max()));
        postcondition(size() == old(size()) - 1);
        postcondition((int res) -> forall((int w) -> count(w) == old(count(w)) - (w == res ? 1 : 0)));

        int top = heap[0];
        size = size - 1;
        heap[0] = heap[size];
        int i = 0;
        while (2 * i + 1 < size) {
            modifies(heap);
            invariant(0 <= i && (size == 0 || i < size));
            invariant(heapExceptBelow(heap, size, i));
            invariant(i <= 0 || 2 * i + 1 >= size || heap[(i - 1) / 2] >= heap[2 * i + 1]);
            invariant(i <= 0 || 2 * i + 2 >= size || heap[(i - 1) / 2] >= heap[2 * i + 2]);

            int child = 2 * i + 1;
            if (child + 1 < size && heap[child + 1] > heap[child]) {
                child = child + 1;
            }
            if (heap[i] >= heap[child]) {
                return top;
            }
            int tmp = heap[i];
            heap[i] = heap[child];
            heap[child] = tmp;
            i = child;
        }
        return top;
    }
}
