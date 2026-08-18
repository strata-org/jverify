package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.postcondition;

/**
 * Negative twin of {@link InstanceMethodVerifies}: the instance-method encoding
 * CHECKS the contract rather than verifying vacuously. The body returns
 * {@code x + 1}, the postcondition claims {@code r == x + 2}, so verification
 * must FAIL. {@code @Unbounded} avoids the unrelated bounded-int overflow
 * obligation, so the sole error is the contract violation. The class is
 * {@code final} so the method is translated (not refused for polymorphic
 * dispatch).
 */
@JVerifyTest(exitCode = 4, methodsVerified = 1, methodsInvalid = 1, errorCount = 1)
final class InstanceMethodContractViolated {

    @Pure
    @Unbounded
    int addOne(@Unbounded int x) {
        postcondition((int r) -> r == x + 2);
//                               ^^^^^^^^^^ Error: postcondition does not hold
        return x + 1;
    }
}
