package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Pure;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * Regression for the {@code @Verify(false)} guard in {@code PureMethodValidator}:
 * only the {@code value} attribute opts a method out of the pure-body SHAPE rules.
 *
 * <p>{@code @Verify(overrideChildren = false)} leaves {@code value} at its default
 * {@code true}, so this method IS verified and the shape rules apply -- the bare
 * non-last call must still be reported. A guard that matched any false attribute
 * would misread {@code overrideChildren = false} as an opt-out and drop the
 * diagnostic.
 */
@JVerifyTest(exitCode = 2)
class PureWithVerifyOverrideChildrenFalse {

    @Pure
    @Verify(overrideChildren = false)
    int pureWithMisplacedCall() {
        foo();
//      ^ error: except for the last statement, a pure block may only contain variable declarations
        return 3;
    }

    void foo() {}
}
