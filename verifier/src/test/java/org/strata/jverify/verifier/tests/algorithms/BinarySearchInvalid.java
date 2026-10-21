package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR}, exitCode = 4)
class BinarySearchInvalid {
    @Pure
    @Erased
    static boolean sorted(int[] a) {
        reads(a);
        return forall((int i, int j) -> implies(0 <= i && i <= j && j < a.length, a[i] <= a[j]));
    }

    @Pure
    @Erased
    static boolean absentIn(int[] a, int key, int lo, int hi) {
        reads(a);
        return forall((int k) -> implies(lo <= k && k < hi, a[k] != key));
    }

    static int binarySearch(int[] a, int key) {
        precondition(sorted(a));
        postcondition((int res) -> (res == -1 && absentIn(a, key, 0, a.length))
                || (0 <= res && res < a.length && a[res] == key));

        int lo = 0;
        int hi = a.length;
        while (lo < hi) {
            invariant(0 <= lo && lo <= hi && hi <= a.length);
//                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
            invariant(absentIn(a, key, 0, lo));
            invariant(absentIn(a, key, hi, a.length));
//                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved

            int mid = lo + (hi - lo) / 2;
            if (a[mid] < key) {
                lo = mid + 1;
            } else if (a[mid] > key) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        return -1;
    }
}
