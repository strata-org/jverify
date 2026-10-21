package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR, Blocker.FRONTEND_OTHER}, exitCode = 4)
class HeapSortInvalid {
    @Pure
    @Erased
    static boolean sortedBetween(int[] a, int lo, int hi) {
        reads(a);
        return forall((int i, int j) -> implies(lo <= i && i <= j && j < hi, a[i] <= a[j]));
    }

    @Pure
    @Erased
    static boolean heapBetween(int[] a, int lo, int hi) {
        reads(a);
        return forall((int k) -> implies(lo <= k && k < hi,
                (2 * k + 1 >= hi || a[k] >= a[2 * k + 1]) && (2 * k + 2 >= hi || a[k] >= a[2 * k + 2])));
    }

    @Pure
    @Erased
    static boolean heapExceptAt(int[] a, int lo, int hi, int root) {
        reads(a);
        return forall((int k) -> implies(lo <= k && k < hi && k != root,
                (2 * k + 1 >= hi || a[k] >= a[2 * k + 1]) && (2 * k + 2 >= hi || a[k] >= a[2 * k + 2])))
                && implies(root > lo,
                    (2 * root + 1 >= hi || a[(root - 1) / 2] >= a[2 * root + 1])
                    && (2 * root + 2 >= hi || a[(root - 1) / 2] >= a[2 * root + 2]));
    }

    @Pure
    @Erased
    static boolean prefixBelowSuffix(int[] a, int end) {
        reads(a);
        return forall((int k, int m) -> implies(0 <= k && k < end && end <= m && m < a.length, a[k] <= a[m]));
    }

    @Pure
    @Erased
    static @Unbounded int count(int[] a, int v, @Unbounded int hi) {
        reads(a);
        decreases(hi);
        if (hi <= 0) {
            return 0;
        }
        return count(a, v, hi - 1) + (a[hi - 1] == v ? 1 : 0);
    }

    @Pure
    @Erased
    static boolean permutation(int[] a, int[] orig) {
        reads(a);
        reads(orig);
        return a.length == orig.length
                && forall((int v) -> count(a, v, a.length) == count(orig, v, orig.length));
    }

    static void siftDown(int[] a, int start, int end) {
        precondition(0 <= start && start < end && end <= a.length && a.length <= 1000000);
        precondition(heapBetween(a, start + 1, end));
        modifies(a);
        postcondition(heapBetween(a, start, end));
//                    ^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved
        postcondition(forall((int k) -> implies(0 <= k && k < a.length && (k < start || end <= k), a[k] == old(a[k]))));
        postcondition(forall((int v) -> count(a, v, a.length) == old(count(a, v, a.length))));

        int root = start;
        while (2 * root + 1 < end) {
            modifies(a);
            invariant(start <= root && root < end);
            invariant(heapExceptAt(a, start, end, root));

            int child = 2 * root;
            if (child + 1 < end && a[child] < a[child + 1]) {
                child = child + 1;
            }
            if (a[root] < a[child]) {
                int tmp = a[root];
                a[root] = a[child];
                a[child] = tmp;
                root = child;
            } else {
                return;
            }
        }
    }

    static void heapSort(int[] a, int[] orig) {
        precondition(permutation(a, orig));
        precondition(a.length <= 1000000);
        modifies(a);
        postcondition(sortedBetween(a, 0, a.length));
        postcondition(permutation(a, orig));

        int n = a.length;
        int start = n / 2 - 1;
        while (start >= 0) {
            modifies(a);
            invariant(-1 <= start && start < n);
            invariant(heapBetween(a, start + 1, n));
            invariant(permutation(a, orig));

            siftDown(a, start, n);
            start = start - 1;
        }

        int end = n - 1;
        while (end > 0) {
            modifies(a);
            invariant((0 <= end || n == 0) && end < n);
            invariant(heapBetween(a, 0, end + 1));
            invariant(sortedBetween(a, end + 1, n));
            invariant(prefixBelowSuffix(a, end + 1));
            invariant(permutation(a, orig));

            int tmp = a[0];
            a[0] = a[end];
            a[end] = tmp;
            siftDown(a, 0, end);
            end = end - 1;
        }
    }
}
