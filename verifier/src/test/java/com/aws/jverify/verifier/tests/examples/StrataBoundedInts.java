package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 0, BACKENDS = { Strata })
class StrataBoundedInts {
    static int addBounded(int x, int y) {
        precondition(x >= 0);
        precondition(x <= 1000);
        precondition(y >= 0);
        precondition(y <= 1000);
        postcondition((int res) -> res == x + y);
        return x + y;
    }
}
