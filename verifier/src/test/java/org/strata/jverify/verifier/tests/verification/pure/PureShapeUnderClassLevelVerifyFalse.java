package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Transparent;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * Regression for the {@code @Verify(false)} guard in {@code TransparentMethodValidator}:
 * a CLASS-level opt-out must suppress the pure-body SHAPE rules for its members,
 * the same way it suppresses the unsupported-construct rules.
 *
 * <p>The shape rules exist to make a {@code @Transparent} body translatable to an
 * expression. A class opted out of verification is never translated, so the rules
 * have nothing to protect and must not fire -- {@code pureWithMisplacedCall} here
 * would otherwise report "except for the last statement, a transparent block may only
 * contain variable declarations" and turn a previously-green build red.
 *
 * <p>The guard cannot read {@code VerifyAnnotationCompiler}'s stacked mode, which
 * runs later, so it walks the enclosing classes itself. {@code Nested} checks that
 * the walk does not stop at the immediately-enclosing class.
 */
@JVerifyTest(exitCode = 0)
@Verify(false)
class PureShapeUnderClassLevelVerifyFalse {

    @Transparent
    int pureWithMisplacedCall() {
        foo();
        return 3;
    }

    void foo() {}

    static class Nested {
        @Transparent
        int alsoOptedOut() {
            foo();
            return 4;
        }

        void foo() {}
    }
}
