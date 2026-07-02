package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.postcondition;

/**
 * Soundness regression: a skipped member's contract must never count as Verified.
 * Constructor translation is deferred, so {@code Box}'s contract is never checked
 * by Strata; its postcondition here is deliberately FALSE. It must be counted
 * Skipped (not Verified — that would be a false Verified). The one Verified method
 * is the outer class's implicit constructor. Before the fix: 2 Verified / 0 errors.
 */
@JVerifyTest(methodsVerified = 1, methodsSkipped = 1, errorCount = 0)
public class SkippedConstructorContractNotVerified {
    static class Box {
        private final int value;

        public Box(int value_) {
            this.value = value_;
            postcondition(this.value == value_ + 1);
        }
    }
}
