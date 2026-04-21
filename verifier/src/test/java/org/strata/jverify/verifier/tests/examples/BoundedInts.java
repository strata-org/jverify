package org.strata.jverify.verifier.tests.examples;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class BoundedInts {
    static int addBounded(int x, int y) {
        precondition(0 <= x && x <= 2000000000);
        precondition(0 <= y && y <= 147483647);
        postcondition((int res) -> res == x + y);
        return x + y;
    }
}
