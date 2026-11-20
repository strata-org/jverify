package org.strata.jverify.verifier.tests.verification;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

/**
 * Regression test for the transitive-emittability fixpoint: when a method is refused
 * (here {@code helper}, unsupported {@code instanceof}), any method that calls it
 * cannot be emitted either — the call would reference an unresolved Laurel name. The
 * fixpoint drops {@code caller} too and reports "call to 'helper', which could not be
 * translated" on it, reported because it is static. {@code unrelated} still verifies.
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
//              ^ error: call to 'helper', which could not be translated
        check(helper(o) || true);
    }

    static void unrelated(int x) {
        check(x == x);
    }
}
