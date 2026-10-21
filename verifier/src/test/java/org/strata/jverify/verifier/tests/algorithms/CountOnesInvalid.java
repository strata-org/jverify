package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER}, exitCode = 4)
class CountOnesInvalid {
    @Pure
    @Erased
    static @Unbounded int onesBelow(boolean[] a, @Unbounded int hi) {
        reads(a);
        decreases(hi);
        if (hi <= 0) {
            return 0;
        }
        return onesBelow(a, hi - 1) + (a[hi - 1] ? 1 : 0);
    }

    static int countOnes(boolean[] a) {
        postcondition((int res) -> res == onesBelow(a, a.length));
//                                 ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved

        int c = 0;
        int i = 0;
        while (i < a.length - 1) {
            invariant(0 <= i && i <= a.length);
            invariant(0 <= c && c <= i);
            invariant(c == onesBelow(a, i));

            if (a[i]) {
                c = c + 1;
            }
            i = i + 1;
        }
        return c;
    }
}
