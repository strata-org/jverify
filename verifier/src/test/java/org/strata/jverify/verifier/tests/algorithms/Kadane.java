package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_OTHER}, exitCode = 0)
class Kadane {
    @Transparent
    @Erased
    static @Unbounded int sumRange(int[] a, @Unbounded int lo, @Unbounded int hi) {
        reads(a);
        decreases(hi - lo);
        if (hi <= lo) {
            return 0;
        }
        return sumRange(a, lo, hi - 1) + a[hi - 1];
    }

    @Transparent
    @Erased
    static boolean isBestUpTo(int[] a, int best, int i) {
        reads(a);
        return forall((int lo, int hi) -> implies(0 <= lo && lo <= hi && hi <= i, sumRange(a, lo, hi) <= best))
                && exists((int lo) -> exists((int hi) -> 0 <= lo && lo <= hi && hi <= i && sumRange(a, lo, hi) == best));
    }

    @Transparent
    @Erased
    static boolean isBestEndingAt(int[] a, int cur, int i) {
        reads(a);
        return forall((int lo) -> implies(0 <= lo && lo <= i, sumRange(a, lo, i) <= cur))
                && exists((int lo) -> 0 <= lo && lo <= i && sumRange(a, lo, i) == cur);
    }

    static int maxSubarray(int[] a) {
        precondition(a.length <= 1000);
        precondition(forall((int k) -> implies(0 <= k && k < a.length, -1000 <= a[k] && a[k] <= 1000)));
        postcondition((int res) -> isBestUpTo(a, res, a.length));

        int best = 0;
        int cur = 0;
        int i = 0;
        while (i < a.length) {
            invariant(0 <= i && i <= a.length);
            invariant(0 <= cur && cur <= 1000 * i);
            invariant(0 <= best && best <= 1000 * i);
            invariant(isBestEndingAt(a, cur, i));
            invariant(isBestUpTo(a, best, i));

            cur = cur + a[i] > 0 ? cur + a[i] : 0;
            best = cur > best ? cur : best;
            i = i + 1;
        }
        return best;
    }
}
