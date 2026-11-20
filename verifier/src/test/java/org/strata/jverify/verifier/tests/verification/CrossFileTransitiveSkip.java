// ^ CrossFileTransitiveSkipHelper.java(4:17-4:18) error: call to 'helper', which could not be translated
package org.strata.jverify.verifier.tests.verification;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

/**
 * Multi-file regression for the diagnostic-attribution fix: when the static caller
 * the fixpoint drops lives in a different compilation unit than the one the
 * translator visited last, the diagnostic must attribute to the caller's own file,
 * not a stale unit. {@code helper} (refused: instanceof) is here; its static caller
 * is in {@code CrossFileTransitiveSkipHelper.java} and must report there.
 */
@JVerifyTest(
        continueOnErrors = true,
        exitCode = 0,
        methodsVerified = 3,
        methodsSkipped = 2,
        errorCount = 0,
        additionalFiles = {"./CrossFileTransitiveSkipHelper.java"}
)
class CrossFileTransitiveSkip {
    static boolean helper(Object o) {
//                 ^ error: instanceof on opaque reference types is not yet supported
        return o instanceof String;
    }

    static void unrelated(int x) {
        check(x == x);
    }
}
