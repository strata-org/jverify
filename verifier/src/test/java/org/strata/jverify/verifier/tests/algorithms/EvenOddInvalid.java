package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Nat;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, errorCount = 1)
class EvenOddInvalid {
    static boolean isEven(@Nat int n) {
        precondition(n >= 0);
        decreases(n);
        postcondition((boolean res) -> res == (n % 2 == 0));

        if (n == 0) {
            return true;
        }
        return isOdd(n - 1);
    }

    static boolean isOdd(@Nat int n) {
        precondition(n >= 0);
        decreases(n);
        postcondition((boolean res) -> res == (n % 2 == 1));
//                                     ^^^^^^^^^^^^^^^^^^^ Error: postcondition does not hold

        if (n == 0) {
            return true;
        }
        return isEven(n - 1);
    }
}
