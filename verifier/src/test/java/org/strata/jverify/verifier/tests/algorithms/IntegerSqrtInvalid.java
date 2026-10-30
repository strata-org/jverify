package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Nat;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, errorCount = 1)
class IntegerSqrtInvalid {
    static int isqrt(@Nat int n) {
        precondition(0 <= n && n <= 46340);
        // Squares in long, as in IntegerSqrt: the int products carry their
        // own overflow obligations, and this fixture is about the missing
        // invariant, not overflow.
        postcondition((int res) -> 0 <= res && (long) res * res <= n && n < ((long) res + 1) * ((long) res + 1));
//                                 ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved

        int r = 0;
        while ((long) r * r <= n) {
            invariant(0 <= r && r <= 46340);

            r = r + 1;
        }
        return r;
    }
}
