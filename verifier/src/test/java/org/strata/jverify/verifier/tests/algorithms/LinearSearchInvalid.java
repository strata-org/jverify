package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_OTHER}, exitCode = 4)
class LinearSearchInvalid {
    @Transparent
    @Erased
    static boolean absentBelow(int[] a, int key, int hi) {
        reads(a);
        return forall((int k) -> implies(0 <= k && k < hi, a[k] != key));
    }

    static int linearSearch(int[] a, int key) {
        postcondition((int res) -> (res == -1 && absentBelow(a, key, a.length))
//                                 ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved
                || (0 <= res && res < a.length && a[res] == key));

        int i = 0;
        while (i < a.length - 1) {
            invariant(0 <= i && i <= a.length);
            invariant(absentBelow(a, key, i));

            if (a[i] == key) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }
}
