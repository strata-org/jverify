package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Negating {@code MIN_VALUE} wraps in Java ({@code -MIN_VALUE == MIN_VALUE}),
 * so it must carry an overflow obligation in both widths. The literal fold
 * in {@code BodyTranslator.checkedNeg} excludes exactly this value: a folded
 * literal carries no obligation, so a stacked {@code Long.MIN_VALUE} (which
 * only hand-crafted bytecode can produce — javac folds the constant
 * expression itself, and an int {@code MIN_VALUE} never reaches the fold
 * because {@code LaurelFactory.longLiteral} stores negative int constants as
 * {@code $neg} applications) would lose its wrap silently. A non-MIN
 * literal still folds and stays obligation-free ({@code negSmall}).
 */
@JVerifyTest(exitCode = 4, errorCount = 2, methodsVerified = 2, methodsInvalid = 2)
class NegatedMinValue {
    static void negIntMin() {
        int a = Integer.MIN_VALUE;
        check(-a > 0);
//      ^^^^^^^^^^^^^^ Error: no int overflow on '-' does not hold
    }

    static void negLongMin() {
        long a = Long.MIN_VALUE;
        check(-a > 0);
//      ^^^^^^^^^^^^^^ Error: no long overflow on '-' does not hold
    }

    static void negSmall() {
        int a = 7;
        check(-a == -7);
    }
}
