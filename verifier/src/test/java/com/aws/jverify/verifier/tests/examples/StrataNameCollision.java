package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

// Tests that "increment" doesn't collide with Strata's built-in heap function,
// and that "compute" doesn't collide across classes.
@JVerifyTest(exitCode = 0, BACKENDS = { Strata },
        additionalFiles = { "StrataNameCollisionHelper.java" })
class StrataNameCollision {
    static int increment(int x) {
        precondition(0 <= x && x < 2147483647);
        return x + 1;
    }

    static int compute(int x) {
        precondition(0 <= x && x <= 1000);
        return x * 2;
    }
}
