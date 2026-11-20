package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Nat;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class IntegerSqrt {
    static int isqrt(@Nat int n) {
        precondition(0 <= n && n <= 46340);
        // The squares are computed in long: under the invariant r <= 46340
        // the int product (r + 1) * (r + 1) really does overflow in Java at
        // r = 46340, so it carries an unprovable obligation. Widening makes
        // the 64-bit obligation trivially dischargeable and matches the
        // idiomatic Java fix for product guards.
        postcondition((int res) -> 0 <= res && (long) res * res <= n
                && n < ((long) res + 1) * ((long) res + 1));

        int r = 0;
        while (((long) r + 1) * ((long) r + 1) <= n) {
            invariant(0 <= r && r <= 46340);
            invariant((long) r * r <= n);

            r = r + 1;
        }
        return r;
    }
}
