package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Nat;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_NONLINEAR_ARITHMETIC}, exitCode = 0)
class Gcd {
    static int gcd(@Nat int a, @Nat int b) {
        precondition(a > 0 && b >= 0);
        decreases(b);
        postcondition((int res) -> res > 0 && a % res == 0 && b % res == 0);

        if (b == 0) {
            return a;
        }
        return gcd(b, a % b);
    }
}
