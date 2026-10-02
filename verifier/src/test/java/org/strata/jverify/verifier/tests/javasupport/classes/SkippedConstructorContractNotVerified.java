package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.postcondition;

/**
 * Soundness regression: a skipped member's contract must never count as Verified.
 * Constructor translation is deferred, so Strata never checks {@code Box}'s
 * contract; its postcondition here is deliberately FALSE. It must count Skipped,
 * not Verified. The one Verified method is the outer class's implicit
 * constructor. Before the fix: 2 Verified / 0 errors.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 1, methodsSkipped = 0, methodsInvalid = 1, errorCount = 1)
public class SkippedConstructorContractNotVerified {
    static class Box {
        private final int value;

        public Box(int value_) {
            this.value = value_;
            postcondition(this.value == value_ + 1);
//          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved
        }
    }
}
