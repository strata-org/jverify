package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Mirrors the intentionally-failing tests from T23_IncrDecr.lean.
 * These verify that the verifier correctly reports errors for wrong assertions
 * about increment/decrement semantics.
 */
@JVerifyTest(exitCode = 4, errorCount = 3)
class IncrementDecrementErrors {
    static void failingAssertOnPostIncr() {
        int x = 0;
        int y = x++;
        // y should be 0 (old value), not 1
        check(y == 1);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void failingAssertOnVarAfterPostIncr() {
        int x = 0;
        int y = x++;
        // x should be 1 (updated), not 0
        check(x == 0);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void failingAssertOnSkippedShortCircuit() {
        int x = 0;
        int y = 0;
        boolean b = x > 0 && y++ > 5;
        // y should be 0 (short-circuited, not incremented)
        check(y == 1);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
