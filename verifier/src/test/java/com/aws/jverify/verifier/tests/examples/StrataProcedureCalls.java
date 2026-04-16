package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 4, errorCount = 1, BACKENDS = { Strata })
class StrataProcedureCalls {
    static int addOne(int x) {
        precondition(x > 0);
        // Overflow guard: x + 1 must fit in int32
        precondition(x < 2147483647);
        return x + 1;
    }

    static void validCall() {
        int r = addOne(5);
    }

    static void invalidCall() {
        int r = addOne(0);
//      ^^^^^^^^^^^^^^^^^^ Error: precondition does not hold
    }
}
