package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 0, BACKENDS = { Strata })
class StrataForLoop {
    static void simpleFor() {
        int sum = 0;
        for (int i = 0; i < 3; i = i + 1) {
            invariant(0 <= sum && sum <= 3);
            invariant(0 <= i && i <= 3);
            invariant(sum == i);
            sum = sum + 1;
        }
        check(sum == 3);
    }
}
