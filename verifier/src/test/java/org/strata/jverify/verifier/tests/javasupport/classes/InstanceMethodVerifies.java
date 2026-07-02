package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Positive happy-path for the static-call-with-self instance-method encoding: an
 * instance method's real contract is actually CHECKED (not vacuously verified)
 * and discharged through the {@code self}-parameter translation.
 *
 * <p>The class is {@code final} so every instance method is provably monomorphic
 * and passes {@code refuseIfPolymorphicDispatch}. Each method carries a real,
 * checkable contract over its own parameters/return; {@code @Unbounded} keeps the
 * arithmetic free of the (unrelated) bounded-int overflow obligation. The
 * negative twin — a violated instance-method contract IS caught — lives in
 * {@link InstanceMethodContractViolated}.
 *
 * <p>Verified count is 4: the three methods plus the implicit constructor.
 */
@JVerifyTest(methodsVerified = 4, errorCount = 0)
final class InstanceMethodVerifies {

    // @Pure instance method proving its own postcondition. @Pure + postcondition
    // emits an opaque procedure taking `self` as its first parameter.
    @Pure
    @Unbounded
    int addOne(@Unbounded int x) {
        postcondition((int r) -> r == x + 1);
        return x + 1;
    }

    // Non-pure instance method with a postcondition over its return value.
    @Unbounded
    int clampLow(@Unbounded int a) {
        postcondition((int r) -> r >= 0);
        return a > 0 ? a : 0;
    }

    // Instance method with a precondition and an in-body check.
    @Unbounded
    int guarded(@Unbounded int a) {
        precondition(a > 0);
        check(a + 1 > a);
        return a;
    }
}
