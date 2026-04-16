package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class StrataPostconditions {
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
