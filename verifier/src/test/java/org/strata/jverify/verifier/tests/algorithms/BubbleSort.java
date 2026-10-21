package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR}, exitCode = 0)
class BubbleSort {
    @Pure
    @Erased
    static boolean sortedBetween(int[] a, int lo, int hi) {
        reads(a);
        return forall((int i, int j) -> implies(lo <= i && i <= j && j < hi, a[i] <= a[j]));
    }

    @Pure
    @Erased
    static boolean prefixBelowSuffix(int[] a, int i) {
        reads(a);
        return forall((int k, int m) -> implies(0 <= k && k < i && i <= m && m < a.length, a[k] <= a[m]));
    }

    @Pure
    @Erased
    static boolean maxAt(int[] a, int j) {
        reads(a);
        return forall((int k) -> implies(0 <= k && k <= j, a[k] <= a[j]));
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

    static void bubbleSort(int[] a, int[] orig) {
        precondition(permutation(a, orig));
        modifies(a);
        postcondition(sortedBetween(a, 0, a.length));
        postcondition(permutation(a, orig));

        int i = a.length;
        while (i > 1) {
            modifies(a);
            invariant(0 <= i && i <= a.length);
            invariant(sortedBetween(a, i, a.length));
            invariant(prefixBelowSuffix(a, i));
            invariant(permutation(a, orig));

            int j = 0;
            while (j < i - 1) {
                modifies(a);
                invariant(0 <= j && j < i);
                invariant(maxAt(a, j));
                invariant(sortedBetween(a, i, a.length));
                invariant(prefixBelowSuffix(a, i));
                invariant(permutation(a, orig));

                if (a[j] > a[j + 1]) {
                    int tmp = a[j];
                    a[j] = a[j + 1];
                    a[j + 1] = tmp;
                }
                j = j + 1;
            }
            i = i - 1;
        }
    }
}
