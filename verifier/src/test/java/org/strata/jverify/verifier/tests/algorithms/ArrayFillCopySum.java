package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_OTHER}, exitCode = 0)
class ArrayFillCopySum {
    @Transparent
    @Erased
    static boolean filledBelow(int[] a, int v, int hi) {
        reads(a);
        return forall((int k) -> implies(0 <= k && k < hi, a[k] == v));
    }

    @Transparent
    @Erased
    static boolean copiedBelow(int[] dst, int[] src, int hi) {
        reads(dst);
        reads(src);
        return forall((int k) -> implies(0 <= k && k < hi, dst[k] == src[k]));
    }

    @Transparent
    @Erased
    static @Unbounded int sumBelow(int[] a, @Unbounded int hi) {
        reads(a);
        decreases(hi);
        if (hi <= 0) {
            return 0;
        }
        return sumBelow(a, hi - 1) + a[hi - 1];
    }

    static void fill(int[] a, int v) {
        modifies(a);
        postcondition(filledBelow(a, v, a.length));

        int i = 0;
        while (i < a.length) {
            modifies(a);
            invariant(0 <= i && i <= a.length);
            invariant(filledBelow(a, v, i));

            a[i] = v;
            i = i + 1;
        }
    }

    static void copy(int[] src, int[] dst) {
        precondition(src.length == dst.length);
        modifies(dst);
        postcondition(copiedBelow(dst, src, src.length));

        int i = 0;
        while (i < src.length) {
            modifies(dst);
            invariant(0 <= i && i <= src.length);
            invariant(copiedBelow(dst, src, i));

            dst[i] = src[i];
            i = i + 1;
        }
    }

    static int sum(int[] a) {
        precondition(a.length <= 1000);
        precondition(forall((int k) -> implies(0 <= k && k < a.length, -1000 <= a[k] && a[k] <= 1000)));
        postcondition((int res) -> res == sumBelow(a, a.length));

        int s = 0;
        int i = 0;
        while (i < a.length) {
            invariant(0 <= i && i <= a.length);
            invariant(s == sumBelow(a, i));
            invariant(-1000 * i <= s && s <= 1000 * i);

            s = s + a[i];
            i = i + 1;
        }
        return s;
    }
}
