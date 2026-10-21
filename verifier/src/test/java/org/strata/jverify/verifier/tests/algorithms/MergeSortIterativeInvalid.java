package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR, Blocker.FRONTEND_OTHER}, exitCode = 4)
class MergeSortIterativeInvalid {
    @Pure
    @Erased
    static boolean sortedBetween(int[] a, int lo, int hi) {
        reads(a);
        return forall((int i, int j) -> implies(lo <= i && i <= j && j < hi, a[i] <= a[j]));
    }

    @Pure
    @Erased
    static boolean blocksSorted(int[] a, int w, int lo) {
        reads(a);
        return forall((int i, int j) -> implies(0 <= i && i <= j && j < a.length
                && ((j < lo && i / (2 * w) == j / (2 * w)) || (lo <= i && i / w == j / w)),
                a[i] <= a[j]));
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

    static void mergeRuns(int[] a, int[] tmp, int lo, int mid, int hi) {
        precondition(0 <= lo && lo <= mid && mid <= hi && hi <= a.length);
        precondition(tmp.length == a.length);
        precondition(sortedBetween(a, lo, mid));
        precondition(sortedBetween(a, mid, hi));
        precondition(a != tmp);
        modifies(a);
        modifies(tmp);
        postcondition(sortedBetween(a, lo, hi));
        postcondition(forall((int k) -> implies(0 <= k && k < a.length && (k < lo || hi <= k), a[k] == old(a[k]))));
        postcondition(forall((int v) -> count(a, v, a.length) == old(count(a, v, a.length))));

        int i = lo;
        int j = mid;
        int k = lo;
        while (k < hi) {
            modifies(tmp);
            invariant(lo <= i && i <= mid && mid <= j && j <= hi);
            invariant(k == i + j - mid);
            invariant(sortedBetween(tmp, lo, k));
            invariant(implies(lo < k && i < mid, tmp[k - 1] <= a[i]));
            invariant(implies(lo < k && j < hi, tmp[k - 1] <= a[j]));

            if (j >= hi || (i < mid && a[i] <= a[j])) {
                tmp[k] = a[i];
                i = i + 1;
            } else {
                tmp[k] = a[j];
                j = j + 1;
            }
            k = k + 1;
        }

        int c = lo;
        while (c < hi) {
            modifies(a);
            invariant(lo <= c && c <= hi);
            invariant(forall((int m) -> implies(0 <= m && m < a.length && (m < lo || hi <= m), a[m] == old(a[m]))));

            a[c] = tmp[c];
            c = c + 1;
        }
    }

    static void mergeSort(int[] a, int[] orig) {
        precondition(permutation(a, orig));
        precondition(a.length <= 536870912);
        modifies(a);
        postcondition(sortedBetween(a, 0, a.length));
        postcondition(permutation(a, orig));

        int n = a.length;
        int[] tmp = new int[n];
        int width = 1;
        while (width < n) {
            modifies(a);
            modifies(tmp);
            invariant(1 <= width && width <= 536870912);
            invariant(tmp.length == n);
            invariant(blocksSorted(a, width, 0));
            invariant(permutation(a, orig));

            int lo = 0;
            while (lo < n) {
                modifies(a);
                modifies(tmp);
                invariant(0 <= lo && (lo % (2 * width) == 0 || lo == n));
                invariant(blocksSorted(a, width, lo));
                invariant(permutation(a, orig));

                int mid = lo + width;
                int hi = lo + 2 * width < n ? lo + 2 * width : n;
                mergeRuns(a, tmp, lo, mid, hi);
//              ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: precondition could not be proved
                lo = hi;
            }
            width = width * 2;
        }
    }
}
