package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_OTHER}, exitCode = 0)
class ArrayReverse {
    @Transparent
    @Erased
    static boolean sameBetween(int[] a, int[] orig, int lo, int hi) {
        reads(a);
        reads(orig);
        return forall((int k) -> implies(lo <= k && k <= hi, a[k] == orig[k]));
    }

    @Transparent
    @Erased
    static boolean swappedBelow(int[] a, int[] orig, int i) {
        reads(a);
        reads(orig);
        return forall((int k) -> implies(0 <= k && k < i,
                a[k] == orig[a.length - 1 - k] && a[a.length - 1 - k] == orig[k]));
    }

    @Transparent
    @Erased
    static boolean isReverse(int[] a, int[] orig) {
        reads(a);
        reads(orig);
        return forall((int k) -> implies(0 <= k && k < a.length, a[k] == orig[a.length - 1 - k]));
    }

    static void reverse(int[] a, int[] orig) {
        precondition(a.length == orig.length);
        precondition(sameBetween(a, orig, 0, a.length - 1));
        precondition(a != orig);
        modifies(a);
        postcondition(isReverse(a, orig));

        int i = 0;
        int j = a.length - 1;
        while (i < j) {
            modifies(a);
            invariant(0 <= i);
            invariant(j == a.length - 1 - i);
            invariant(i <= j + 1);
            invariant(a.length == orig.length);
            invariant(swappedBelow(a, orig, i));
            invariant(sameBetween(a, orig, i, j));

            int tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
            i = i + 1;
            j = j - 1;
        }
    }
}
