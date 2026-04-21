package org.strata.jverify.verifier.tests.examples;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class Postconditions {
    static int addOne(int x) {
        precondition(0 <= x && x < 2147483647);
        postcondition((int res) -> res == x + 1);
        return x + 1;
    }

    static int clamp(int x, int lo, int hi) {
        precondition(lo <= hi);
        precondition(-1000 <= x && x <= 1000);
        postcondition((int res) -> lo <= res && res <= hi);
        if (x < lo) return lo;
        if (x > hi) return hi;
        return x;
    }
}
