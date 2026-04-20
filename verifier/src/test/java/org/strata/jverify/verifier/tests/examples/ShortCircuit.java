package org.strata.jverify.verifier.tests.examples;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class ShortCircuit {
    // Short-circuit ||: y == 0 prevents evaluation of x / y
    static void orElseGuardsDivision(int x, int y) {
        check(y == 0 || x / y == x / y);
    }

    // Short-circuit &&: y != 0 guards the division
    static void andThenGuardsDivision(int x, int y) {
        check(!(y != 0 && x / y != x / y));
    }
}
