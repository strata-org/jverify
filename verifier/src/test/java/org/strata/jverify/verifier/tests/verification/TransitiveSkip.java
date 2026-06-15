package org.strata.jverify.verifier.tests.verification;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

/**
 * Regression test for the transitive-emittability fixpoint: when a method is
 * refused (here {@code helper}, which uses an unsupported {@code instanceof}),
 * any method that calls it ({@code caller}) cannot be emitted either — its call
 * would reference an unresolved Laurel name. The fixpoint drops {@code caller}
 * too, reporting "call to a method that could not be translated" on it (it is
 * static, so the drop is reported), and an unrelated method ({@code unrelated})
 * still verifies.
 */
@JVerifyTest(
        continueOnErrors = true,
        exitCode = 0,
        methodsVerified = 2,
        methodsSkipped = 2,
        errorCount = 0
)
class TransitiveSkip {
    static boolean helper(Object o) {
//                 ^ error: instanceof on opaque reference types is not yet supported
        return o instanceof String;
    }

    static void caller(Object o) {
//              ^ error: call to a method that could not be translated
        check(helper(o) || true);
    }

    static void unrelated(int x) {
        check(x == x);
    }
}
