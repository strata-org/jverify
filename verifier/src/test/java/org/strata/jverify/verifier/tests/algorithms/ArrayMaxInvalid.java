package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_OTHER}, exitCode = 4)
class ArrayMaxInvalid {
    @Transparent
    @Erased
    static boolean boundsBelow(int[] a, int m, int hi) {
        reads(a);
        return forall((int k) -> implies(0 <= k && k < hi, a[k] <= m));
    }

    @Transparent
    @Erased
    static boolean occursBelow(int[] a, int m, int hi) {
        reads(a);
        return exists((int k) -> 0 <= k && k < hi && a[k] == m);
    }

    static int max(int[] a) {
        precondition(a.length > 0);
        postcondition((int res) -> boundsBelow(a, res, a.length) && occursBelow(a, res, a.length));

        int m = a[0];
        int i = 1;
        while (i < a.length) {
            invariant(1 <= i && i <= a.length);
            invariant(boundsBelow(a, m, i));
//                    ^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
            invariant(occursBelow(a, m, i));

            if (a[i] < m) {
                m = a[i];
            }
            i = i + 1;
        }
        return m;
    }
}
