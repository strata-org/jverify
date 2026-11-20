package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_OTHER}, exitCode = 0)
class DutchFlag {
    @Transparent
    @Erased
    static int rank(int x, int p) {
        return x < p ? 0 : (x == p ? 1 : 2);
    }

    @Transparent
    @Erased
    static boolean classified(int[] a, int p) {
        reads(a);
        return forall((int i, int j) -> implies(0 <= i && i <= j && j < a.length, rank(a[i], p) <= rank(a[j], p)));
    }

    @Transparent
    @Erased
    static boolean regions(int[] a, int p, int lt, int i, int gt) {
        reads(a);
        return forall((int k) -> implies(0 <= k && k < lt, a[k] < p))
                && forall((int k) -> implies(lt <= k && k < i, a[k] == p))
                && forall((int k) -> implies(gt <= k && k < a.length, a[k] > p));
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

    static void partition(int[] a, int[] orig, int p) {
        precondition(permutation(a, orig));
        modifies(a);
        postcondition(classified(a, p));
        postcondition(permutation(a, orig));

        int lt = 0;
        int i = 0;
        int gt = a.length;
        while (i < gt) {
            modifies(a);
            invariant(0 <= lt && lt <= i && i <= gt && gt <= a.length);
            invariant(regions(a, p, lt, i, gt));
            invariant(permutation(a, orig));

            if (a[i] < p) {
                int tmp = a[lt];
                a[lt] = a[i];
                a[i] = tmp;
                lt = lt + 1;
                i = i + 1;
            } else if (a[i] > p) {
                gt = gt - 1;
                int tmp = a[gt];
                a[gt] = a[i];
                a[i] = tmp;
            } else {
                i = i + 1;
            }
        }
    }
}
