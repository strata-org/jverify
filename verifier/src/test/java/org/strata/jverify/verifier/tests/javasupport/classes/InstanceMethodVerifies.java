package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Positive happy-path for native instance-procedure emission: instance-method
 * contracts are CHECKED (not vacuously verified) through the {@code self?}
 * receiver parameter. The class is {@code final} so calls are monomorphic (pass
 * refuseIfPolymorphicDispatch); {@code @Unbounded} avoids the unrelated
 * bounded-int overflow obligation. Verified 4 = 3 methods + implicit constructor.
 * Negative twin: {@link InstanceMethodContractViolated}.
 */
@JVerifyTest(methodsVerified = 4, errorCount = 0)
final class InstanceMethodVerifies {

    // @Pure instance method proving its own postcondition. @Pure + postcondition
    // emits an opaque procedure taking `self` first.
    @Pure
    @Unbounded
    int addOne(@Unbounded int x) {
        postcondition((int r) -> r == x + 1);
        return x + 1;
    }

    // Non-pure, postcondition over the return value.
    @Unbounded
    int clampLow(@Unbounded int a) {
        postcondition((int r) -> r >= 0);
        return a > 0 ? a : 0;
    }

    // Precondition plus an in-body check.
    @Unbounded
    int guarded(@Unbounded int a) {
        precondition(a > 0);
        check(a + 1 > a);
        return a;
    }
}
