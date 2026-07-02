package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.postcondition;

/**
 * Soundness regression: a skipped member's contract must never be counted
 * Verified.
 *
 * <p>Constructor translation is deferred, so this constructor is emitted to no
 * Laurel and Strata never checks it. Its postcondition here is deliberately
 * FALSE ({@code this.value == value_ + 1} after assigning {@code value_}). If the
 * constructor were counted Verified, JVerify would report a passing verification
 * of a contract that cannot hold — a false Verified. The fix demotes any skipped
 * non-generated member to Skipped, so the expected outcome is Skipped, not
 * Verified, with zero verification errors (nothing was checked). The single
 * Verified method is the outer class's implicit constructor.
 *
 * <p>Before the fix this reported methodsVerified = 2 / errorCount = 0, silently
 * passing the false postcondition.
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
