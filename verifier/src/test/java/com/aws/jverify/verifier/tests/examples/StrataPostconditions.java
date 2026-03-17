package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 4, errorCount = 2, BACKENDS = { Strata })
class StrataPostconditions {
    static int clamp(int x) {
        postcondition((int r) -> r >= 0);
        if (x > 0) {
            return x;
        } else {
            return 0;
        }
    }

    static void useClamp() {
        int r = clamp(5);
        check(r >= 0);
        check(r == 5);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
