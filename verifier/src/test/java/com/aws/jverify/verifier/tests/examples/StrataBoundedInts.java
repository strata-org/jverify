package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class StrataBoundedInts {
    static int addBounded(int x, int y) {
        precondition(0 <= x && x <= 2000000000);
        precondition(0 <= y && y <= 147483647);
        postcondition((int res) -> res == x + y);
        return x + y;
    }
}
