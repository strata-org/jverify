package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_OTHER}, exitCode = 4)
class PrimalityInvalid {
    @Transparent
    @Erased
    static boolean noDivisorBelow(int n, int limit) {
        return forall((int k) -> implies(2 <= k && k < limit, n % k != 0));
    }

    static boolean isPrime(int n) {
        precondition(2 <= n && n <= 10000);
        postcondition((boolean res) -> res == noDivisorBelow(n, n));

        int d = 3;
        while (d < n) {
            invariant(2 <= d && d <= n);
//                    ^^^^^^^^^^^^^^^^ Error: assertion does not hold
            invariant(noDivisorBelow(n, d));
//                    ^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold

            if (n % d == 0) {
                return false;
            }
            d = d + 1;
        }
        return true;
    }
}
