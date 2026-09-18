package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.throwsOn;

/**
 * The callee-side FORCING failure, pinned red: a case's condition claims the
 * method throws whenever it holds, so a guard broader than the actual
 * throwing condition must fail -- the accept-side caller tests cannot catch
 * this (a caller only sees a contract that already verified). Kept in its
 * own file: a resolution-stage refusal elsewhere in the file would abort
 * before the analyze phase emits the forcing obligation, suppressing this
 * failure (which is why it does not live in ThrowsOnProofFailures).
 */
@JVerifyTest(
        exitCode = 4, // verification errors present
        methodsVerified = 3, // fine() + 2 implicit constructors
        methodsInvalid = 1, // guardTooBroad(); also arms the errorCount expectation
        errorCount = 1
)
class ThrowsOnForcingFailure {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'

    /** The guard covers 0 <= x < 10, where the body does NOT throw. */
    static int guardTooBroad(int x) throws AppException {
        throwsOn(x < 10);
//               ^^^^^^ Error: throwsOn case forces a throw does not hold
        if (x < 0) {
            throw new AppException();
        }
        return x;
    }

    /** Siblings stay verifiable. */
    static int fine(int x) {
        return x;
    }
}
