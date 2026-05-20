package org.strata.jverify.verifier.tests.examples;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// Tests that "increment" doesn't collide with Strata's built-in heap function,
// and that "compute" doesn't collide across classes.
@JVerifyTest(exitCode = 0, methodsVerified = 5,
        additionalFiles = { "./NameCollisionHelper.java" })
class NameCollision {
    static int increment(int x) {
        precondition(0 <= x && x < 2147483647);
        return x + 1;
    }

    static int compute(int x) {
        precondition(0 <= x && x <= 1000);
        return x * 2;
    }
}
