package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Nat;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class IntegerSqrt {
    static int isqrt(@Nat int n) {
        precondition(0 <= n && n <= 46340);
        postcondition((int res) -> 0 <= res && res * res <= n && n < (res + 1) * (res + 1));

        int r = 0;
        while ((r + 1) * (r + 1) <= n) {
            invariant(0 <= r && r <= 46340);
            invariant(r * r <= n);

            r = r + 1;
        }
        return r;
    }
}
