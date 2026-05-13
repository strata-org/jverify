package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

/**
 * Tests for prefix and postfix increment/decrement operators.
 *
 * Note: both prefix and postfix are currently lowered identically to x = x ± 1,
 * so the expression value is always the new value. Correct postfix semantics
 * (returning the old value) requires Laurel IR support for block expressions.
 */
@SuppressWarnings("ConstantValue")
@JVerifyTest(methodsVerified = 5, errorCount = 0)
class IncrementDecrementVerification {

    /** Postfix ++ increments the variable. */
    static void postfixIncrement() {
        var x = 5;
        x++;
        check(x == 6);
    }

    /** Prefix ++ increments the variable. */
    static void prefixIncrement() {
        var x = 5;
        ++x;
        check(x == 6);
    }

    /** Postfix -- decrements the variable. */
    static void postfixDecrement() {
        var x = 10;
        x--;
        check(x == 9);
    }

    /** Prefix -- decrements the variable. */
    static void prefixDecrement() {
        var x = 10;
        --x;
        check(x == 9);
    }
}
