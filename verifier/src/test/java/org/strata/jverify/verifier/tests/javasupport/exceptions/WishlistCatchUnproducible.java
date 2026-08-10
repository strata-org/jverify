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
        continueOnErrors = true, exitCode = 0, errorCount = 1)
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
