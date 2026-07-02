package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A parameter-dependent {@code @Pure} method's result CAN be used by a caller
 * when the call appears directly in the assertion/contract: the transparent
 * function inlines at the use site, so the caller sees {@code addOne(a) == a+1}.
 *
 * <p>Contrast {@link PureCallResultViaLocal}, where binding the same call to an
 * intermediate local first loses the relationship to {@code a} — a Strata prover
 * limitation, not a front-end one. Verified count is 4 (three methods + implicit
 * constructor).
 */
@JVerifyTest(methodsVerified = 4, errorCount = 0)
final class PureCallResultUsedDirectly {

    @Pure
    @Unbounded
    static int addOne(@Unbounded int x) {
        return x + 1;
    }

    // Call used directly inside the check.
    static void inCheck(@Unbounded int a) {
        check(addOne(a) == a + 1);
    }

    // Call used directly inside the postcondition.
    @Unbounded
    static int inPostcondition(@Unbounded int a) {
        postcondition((int r) -> r == a + 1);
        return addOne(a);
    }
}
