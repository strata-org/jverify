package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Nat;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class Ackermann {
    static @Unbounded @Nat int ack(@Unbounded @Nat int m, @Unbounded @Nat int n) {
        precondition(m >= 0 && n >= 0);
        decreases(m, n);
        postcondition((int res) -> res > 0);

        if (m == 0) {
            return n + 1;
        }
        if (n == 0) {
            return ack(m - 1, 1);
        }
        return ack(m - 1, ack(m, n - 1));
    }
}
