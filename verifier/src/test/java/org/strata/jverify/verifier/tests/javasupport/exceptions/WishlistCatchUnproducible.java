package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * Wishlist: catching a type no reachable
 * throw can produce should be a per-construct diagnostic, not a silently
 * dead arm. Implicit runtime exceptions are proof obligations -- the NPE
 * here is proven absent, so this handler can never run; today it verifies
 * vacuously, which hides the dead code. The test states the future
 * expectation (one error) and is expected to fail until the diagnostic
 * exists.
 *
 * <p>When closing this gap, also settle the exit code: as declared
 * (exitCode = 0) the XPASS tripwire assumes a frontend refusal/skip; if the
 * diagnostic instead surfaces on the verify path, exitCode must become 4 or
 * the assertion is swallowed as still-blocked forever.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0,
        methodsVerified = 1, // the test class's implicit constructor
        methodsSkipped = 1,
        methodsInvalid = 0, // arms the errorCount expectation (inert without it)
        errorCount = 0)
class WishlistCatchUnproducible {
    static int deadHandler(int x) {
        try {
            return x + 0;
        } catch (NullPointerException e) {
//               ^ error: no reachable throw can produce 'java.lang.NullPointerException'; this handler is dead code
            return -1;
        }
    }
}
