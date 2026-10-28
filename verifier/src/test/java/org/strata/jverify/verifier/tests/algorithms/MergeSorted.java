package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_OTHER}, exitCode = 0)
class MergeSorted {
    @Transparent
    @Erased
    static boolean sortedBetween(int[] a, int lo, int hi) {
        reads(a);
        return forall((int i, int j) -> implies(lo <= i && i <= j && j < hi, a[i] <= a[j]));
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
    static boolean countsMerged(int[] c, int[] a, int[] b, int i, int j) {
        reads(c);
        reads(a);
        reads(b);
        return forall((int v) -> count(c, v, i + j) == count(a, v, i) + count(b, v, j));
    }

    static void merge(int[] a, int[] b, int[] c) {
        precondition(sortedBetween(a, 0, a.length));
        precondition(sortedBetween(b, 0, b.length));
        precondition(a.length <= 100000 && b.length <= 100000);
        precondition(c.length == a.length + b.length);
        modifies(c);
        postcondition(sortedBetween(c, 0, c.length));
        postcondition(countsMerged(c, a, b, a.length, b.length));

        int i = 0;
        int j = 0;
        int k = 0;
        while (k < c.length) {
            modifies(c);
            invariant(0 <= i && i <= a.length);
            invariant(0 <= j && j <= b.length);
            invariant(k == i + j);
            invariant(sortedBetween(c, 0, k));
            invariant(implies(k > 0 && i < a.length, c[k - 1] <= a[i]));
            invariant(implies(k > 0 && j < b.length, c[k - 1] <= b[j]));
            invariant(countsMerged(c, a, b, i, j));

            if (j >= b.length || (i < a.length && a[i] <= b[j])) {
                c[k] = a[i];
                i = i + 1;
            } else {
                c[k] = b[j];
                j = j + 1;
            }
            k = k + 1;
        }
    }
}
