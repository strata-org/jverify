package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@SuppressWarnings({"ConstantValue"})
@JVerifyTest(exitCode = 4, methodsVerified = 4, errorCount = 1)
class VerifyDoWhile {
    /**
     * do-while with break exiting the loop early.
     */
    static void doWhileWithBreak() {
        int x = 0;
        do {
            invariant(x >= 0 && x <= 3);
            if (x == 3) {
                break;
            }
            x = x + 1;
        } while (x < 10);
        check(x == 3);
    }

    /**
     * do-while with continue re-evaluating the condition.
     * When continue is hit, execution should skip the rest of the body
     * but still check the while-condition.
     */
    static void doWhileWithContinue() {
        int x = 0;
        int sum = 0;
        do {
            invariant(x >= 0 && x <= 5);
            invariant(x <= 2 ? sum == x : sum == 2);
            if (x >= 2) {
                x = x + 1;
                continue;
            }
            sum = sum + 1;
            x = x + 1;
        } while (x < 5);
        check(sum == 2);
    }

    /**
     * Numeric loop variable bounded by the invariant.
     * Exercises the sentinel-weakening case (Bug 2) from the original review in #418.
     */
    static void doWhileNumericInvariant() {
        int x = 0;
        do {
            invariant(0 <= x && x < 100);
            x = x + 1;
        } while (x < 5);
        check(x >= 5);
    }

    /**
     * Negative regression test for unsoundness case (Bug 3) from the original review in #418:
     * The invariant is FALSE on the first iteration (x == -1 violates x >= 0).
     * This MUST be rejected by the verifier.
     */
    static void doWhileBadInitialInvariant() {
        int x = -1;
        do { invariant(x >= 0); x = x + 1; } while (x < 5);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }
}
