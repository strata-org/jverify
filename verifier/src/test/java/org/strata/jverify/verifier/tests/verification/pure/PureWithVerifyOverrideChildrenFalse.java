package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Transparent;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.postcondition;

/**
 * Regression for the {@code @Verify(false)} guard in {@code TransparentMethodValidator}:
 * only the {@code value} attribute opts a method out of its checks.
 *
 * <p>{@code @Verify(overrideChildren = false)} leaves {@code value} at its default
 * {@code true}, so this method IS verified and its postcondition must still be
 * reported. A guard that matched any false attribute would misread
 * {@code overrideChildren = false} as an opt-out and drop the diagnostic.
 */
@JVerifyTest(exitCode = 2)
class PureWithVerifyOverrideChildrenFalse {

    @Transparent
    @Verify(overrideChildren = false)
    int withPostcondition() {
        postcondition((int r) -> r == 3);
//                   ^ error: postconditions are not allowed in a transparent method
        return 3;
    }
}
