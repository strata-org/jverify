package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_OTHER}, exitCode = 0)
class SelectionSort {
    @Transparent
    @Erased
    static boolean sortedBetween(int[] a, int lo, int hi) {
        reads(a);
        return forall((int i, int j) -> implies(lo <= i && i <= j && j < hi, a[i] <= a[j]));
    }

    @Transparent
    @Erased
    static boolean prefixBelowSuffix(int[] a, int i) {
        reads(a);
        return forall((int k, int m) -> implies(0 <= k && k < i && i <= m && m < a.length, a[k] <= a[m]));
    }

    @Transparent
    @Erased
    static boolean minBetween(int[] a, int idx, int lo, int hi) {
        reads(a);
        return forall((int k) -> implies(lo <= k && k < hi, a[idx] <= a[k]));
    }

    @Transparent
    @Erased
    static @Unbounded int count(int[] a, int v, @Unbounded int hi) {
        reads(a);
        decreases(hi);
        if (hi <= 0) {
            return 0;
        }
        return count(a, v, hi - 1) + (a[hi - 1] == v ? 1 : 0);
    }

    @Transparent
    @Erased
    static boolean permutation(int[] a, int[] orig) {
        reads(a);
        reads(orig);
        return a.length == orig.length
                && forall((int v) -> count(a, v, a.length) == count(orig, v, orig.length));
    }

    static void selectionSort(int[] a, int[] orig) {
        precondition(permutation(a, orig));
        modifies(a);
        postcondition(sortedBetween(a, 0, a.length));
        postcondition(permutation(a, orig));

        int i = 0;
        while (i < a.length) {
            modifies(a);
            invariant(0 <= i && i <= a.length);
            invariant(sortedBetween(a, 0, i));
            invariant(prefixBelowSuffix(a, i));
            invariant(permutation(a, orig));

            int min = i;
            int j = i + 1;
            while (j < a.length) {
                invariant(i < j && j <= a.length);
                invariant(i <= min && min < j);
                invariant(minBetween(a, min, i, j));

                if (a[j] < a[min]) {
                    min = j;
                }
                j = j + 1;
            }

            int tmp = a[i];
            a[i] = a[min];
            a[min] = tmp;
            i = i + 1;
        }
    }
}
