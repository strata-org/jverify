package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

/**
 * Regression test for #414. Pairs the happy path (translateType +
 * convertLiteral round-trip) with a negative case that fails when c == 65535,
 * asserting the 0..65535 constraint is actually enforced rather than dropped.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 2, errorCount = 1)
class CharPrimitive {
    static void roundTrip(char c) {
        check(c == c);
    }

    static void rangeBoundsBad(char c) {
        check(c <= 65534);
//      ^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
