package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A {@code for} loop with no invariant: the check after it does not hold, and
 * neither does the {@code int} range check on the step, since without an
 * invariant nothing bounds {@code i} at the loop head.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 1, methodsInvalid = 1, errorCount = 3)
class BareForLoop {
    static void countWithoutInvariant() {
        int i = 0;
        for (i = 0; i < 5; i = i + 1) {
//                         ^^^^^^^^^ Error: assertion does not hold
//                         ^^^^^^^^^ Error: no int overflow on '+' does not hold
        }
        check(i == 5);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
