package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * Wishlist (docs/exceptions-design.md §5): catching a type no reachable
 * throw can produce should be a per-construct diagnostic, not a silently
 * dead arm. Implicit runtime exceptions are proof obligations -- the NPE
 * here is proven absent, so this handler can never run; today it verifies
 * vacuously, which hides the dead code. The test states the future
 * expectation (one error) and is expected to fail until the diagnostic
 * exists.
 */
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_EXCEPTIONS},
        continueOnErrors = true, exitCode = 0,
        methodsVerified = 1, // the test class's implicit constructor
        methodsSkipped = 1,
        methodsInvalid = 0, // arms the errorCount expectation (inert without it)
        errorCount = 1)
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
