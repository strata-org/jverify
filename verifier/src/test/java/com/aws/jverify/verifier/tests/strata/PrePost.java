package com.aws.jverify.verifier.tests.strata;

import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 0, methodsVerified = 3, errorCount = 0, BACKENDS = {Strata})
class PrePost {
    static int increment(int x) {
        precondition(x >= 0);
        postcondition((int res) -> res == x + 1);
        return x + 1;
    }

    static int clamp(int x, int lo, int hi) {
        precondition(lo <= hi);
        precondition(x >= -1000 && x <= 1000);
        postcondition((int res) -> res >= lo);
        postcondition((int res) -> res <= hi);
        if (x < lo) return lo;
        if (x > hi) return hi;
        return x;
    }
}
