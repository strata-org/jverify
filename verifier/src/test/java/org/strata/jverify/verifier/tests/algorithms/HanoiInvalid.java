package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nat;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_DECREASES}, exitCode = 4)
class HanoiInvalid {
    @Pure
    @Erased
    static @Unbounded @Nat int pow2(@Unbounded @Nat int n) {
        decreases(n);
        if (n == 0) {
            return 1;
        }
        return 2 * pow2(n - 1);
    }

    static int moves(@Nat int n) {
        precondition(0 <= n && n <= 20);
        decreases(n);
        postcondition((int res) -> res == pow2(n) - 1);
//                                 ^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved

        if (n == 0) {
            return 0;
        }
        return 2 * moves(n - 1) + 2;
    }
}
