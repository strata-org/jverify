package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nat;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_DECREASES}, exitCode = 4)
class FactorialInvalid {
    @Pure
    @Erased
    static @Unbounded @Nat int factSpec(@Unbounded @Nat int n) {
        decreases(n);
        if (n == 0) {
            return 1;
        }
        return n * factSpec(n - 1);
    }

    static int factorial(@Nat int n) {
        precondition(0 <= n && n <= 13);
        decreases(n);
        postcondition((int res) -> res == factSpec(n));

        if (n == 0) {
            return 1;
        }
        return n * factorial(n - 1);
//             ^^^^^^^^^^^^^^^^^^^^ Error: value does not satisfy the subset constraints of 'int32'
    }
}
