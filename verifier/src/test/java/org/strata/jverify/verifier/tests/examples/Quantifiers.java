package org.strata.jverify.verifier.tests.examples;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class Quantifiers {
    static void universalQuantifier() {
        check(forall((int x) -> implies(x > 0, x >= 1)));
    }

    static void existentialQuantifier() {
        check(exists((int x) -> x > 100));
    }

    // Test that quantifier bound variable is constrained to int32 range
    static void boundedQuantifier() {
        check(forall((int x) -> -2147483648 <= x && x <= 2147483647));
    }
}
