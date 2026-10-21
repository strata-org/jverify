package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR, Blocker.FRONTEND_OTHER}, exitCode = 0)
class CoincidenceCount {
    @Pure
    @Erased
    static boolean strictlyIncreasing(int[] a) {
        reads(a);
        return forall((int i, int j) -> implies(0 <= i && i < j && j < a.length, a[i] < a[j]));
    }

    @Pure
    @Erased
    static boolean contains(int[] b, int x) {
        reads(b);
        return exists((int k) -> 0 <= k && k < b.length && b[k] == x);
    }

    @Pure
    @Erased
    static @Unbounded int commonBelow(int[] a, int[] b, @Unbounded int hi) {
        reads(a);
        reads(b);
        decreases(hi);
        if (hi <= 0) {
            return 0;
        }
        return commonBelow(a, b, hi - 1) + (contains(b, a[hi - 1]) ? 1 : 0);
    }

    @Pure
    @Erased
    static boolean bBelowAi(int[] a, int[] b, int i, int j) {
        reads(a);
        reads(b);
        return implies(i < a.length, forall((int m) -> implies(0 <= m && m < j, b[m] < a[i])));
    }

    @Pure
    @Erased
    static boolean aBelowBj(int[] a, int[] b, int i, int j) {
        reads(a);
        reads(b);
        return implies(j < b.length, forall((int k) -> implies(0 <= k && k < i, a[k] < b[j])));
    }

    static int coincidences(int[] a, int[] b) {
        precondition(strictlyIncreasing(a));
        precondition(strictlyIncreasing(b));
        postcondition((int res) -> res == commonBelow(a, b, a.length));

        int i = 0;
        int j = 0;
        int c = 0;
        while (i < a.length && j < b.length) {
            invariant(0 <= i && i <= a.length);
            invariant(0 <= j && j <= b.length);
            invariant(0 <= c && c <= i);
            invariant(c == commonBelow(a, b, i));
            invariant(bBelowAi(a, b, i, j));
            invariant(aBelowBj(a, b, i, j));

            if (a[i] == b[j]) {
                c = c + 1;
                i = i + 1;
                j = j + 1;
            } else if (a[i] < b[j]) {
                i = i + 1;
            } else {
                j = j + 1;
            }
        }
        return c;
    }
}
