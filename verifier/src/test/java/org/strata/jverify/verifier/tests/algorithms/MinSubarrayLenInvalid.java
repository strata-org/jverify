package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_OTHER}, exitCode = 4)
class MinSubarrayLenInvalid {
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
    static boolean noWindowShorterThan(int[] a, int target, int len, int endBefore) {
        reads(a);
        return forall((int lo, int hi) -> implies(0 <= lo && lo < hi && hi <= endBefore && hi - lo < len,
                sumRange(a, lo, hi) < target));
    }

    @Transparent
    @Erased
    static boolean someWindowOfLength(int[] a, int target, int len) {
        reads(a);
        return exists((int lo) -> 0 <= lo && lo + len <= a.length && sumRange(a, lo, lo + len) >= target);
    }

    static int minSubarrayLen(int[] a, int target) {
        precondition(a.length <= 1000);
        precondition(1 <= target && target <= 1000000);
        precondition(forall((int k) -> implies(0 <= k && k < a.length, 1 <= a[k] && a[k] <= 1000)));
        postcondition((int res) -> res == 0
                ? noWindowShorterThan(a, target, a.length + 1, a.length)
                : (1 <= res && res <= a.length
                    && someWindowOfLength(a, target, res)
                    && noWindowShorterThan(a, target, res, a.length)));

        int n = a.length;
        int best = n + 1;
        int lo = 0;
        int sum = 0;
        int hi = 0;
        while (hi < n) {
            invariant(0 <= lo && lo <= hi && hi <= n);
            invariant(sum == sumRange(a, lo, hi));
            invariant(1 <= best && best <= n + 1);
            invariant(noWindowShorterThan(a, target, best, hi));
//                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
            invariant(implies(best <= n, someWindowOfLength(a, target, best)));

            sum = sum + a[hi];
            hi = hi + 1;
            while (sum > target) {
                invariant(0 <= lo && lo <= hi);
                invariant(sum == sumRange(a, lo, hi));

                if (hi - lo < best) {
                    best = hi - lo;
                }
                sum = sum - a[lo];
                lo = lo + 1;
            }
        }
        return best == n + 1 ? 0 : best;
    }
}
