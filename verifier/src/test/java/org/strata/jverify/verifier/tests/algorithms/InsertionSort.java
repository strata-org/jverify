package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR, Blocker.FRONTEND_OTHER}, exitCode = 0)
class InsertionSort {
    @Pure
    @Erased
    static boolean sortedBetween(int[] a, int lo, int hi) {
        reads(a);
        return forall((int i, int j) -> implies(lo <= i && i <= j && j < hi, a[i] <= a[j]));
    }

    @Pure
    @Erased
    static boolean leqAcross(int[] a, int j, int i) {
        reads(a);
        return forall((int k, int m) -> implies(0 <= k && k < j && j < m && m <= i, a[k] <= a[m]));
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

    static void insertionSort(int[] a, int[] orig) {
        precondition(permutation(a, orig));
        modifies(a);
        postcondition(sortedBetween(a, 0, a.length));
        postcondition(permutation(a, orig));

        int i = 0;
        while (i < a.length) {
            modifies(a);
            invariant(0 <= i && i <= a.length);
            invariant(sortedBetween(a, 0, i));
            invariant(permutation(a, orig));

            int j = i;
            while (j > 0 && a[j - 1] > a[j]) {
                modifies(a);
                invariant(0 <= j && j <= i);
                invariant(sortedBetween(a, 0, j));
                invariant(sortedBetween(a, j, i + 1));
                invariant(leqAcross(a, j, i));
                invariant(permutation(a, orig));

                int tmp = a[j - 1];
                a[j - 1] = a[j];
                a[j] = tmp;
                j = j - 1;
            }
            i = i + 1;
        }
    }
}
