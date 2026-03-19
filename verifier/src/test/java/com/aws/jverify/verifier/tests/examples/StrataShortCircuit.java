package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 0, BACKENDS = { Strata })
class StrataShortCircuit {
    // Short-circuit ||: y == 0 prevents evaluation of x / y
    static void orElseGuardsDivision(int x, int y) {
        check(y == 0 || x / y == x / y);
    }

    // Short-circuit &&: y != 0 guards the division
    static void andThenGuardsDivision(int x, int y) {
        check(!(y != 0 && x / y != x / y));
    }
}
