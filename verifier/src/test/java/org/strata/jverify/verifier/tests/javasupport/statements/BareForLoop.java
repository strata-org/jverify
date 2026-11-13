package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A {@code for} loop with no invariant: neither the check after it nor the
 * {@code int} range check on the step is proved, since without an invariant
 * nothing bounds {@code i} at the loop head. Loop elimination replaces the loop
 * by one arbitrary iteration from a havocked state, so both counterexamples
 * come from that state rather than from a run, and are reported as "could not
 * be proved".
 */
@JVerifyTest(exitCode = 4, methodsVerified = 1, methodsInvalid = 1, errorCount = 3)
class BareForLoop {
    static void countWithoutInvariant() {
        int i = 0;
        for (i = 0; i < 5; i = i + 1) {
//                         ^^^^^^^^^ Error: assertion could not be proved
//                         ^^^^^^^^^ Error: no int overflow on '+' could not be proved
        }
        check(i == 5);
//      ^^^^^^^^^^^^^ Error: assertion could not be proved
    }
}
