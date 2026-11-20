package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Nat;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class EvenOdd {
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

        if (n == 0) {
            return false;
        }
        return isEven(n - 1);
    }
}
