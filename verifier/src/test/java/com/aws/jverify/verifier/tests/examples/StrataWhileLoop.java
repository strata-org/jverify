package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 0, BACKENDS = { Strata })
class StrataWhileLoop {
    static void countDown() {
        int i = 3;
        while (i > 0) {
            invariant(i >= 0);
            invariant(i <= 3);
            i = i - 1;
        }
        check(i == 0);
    }
}
