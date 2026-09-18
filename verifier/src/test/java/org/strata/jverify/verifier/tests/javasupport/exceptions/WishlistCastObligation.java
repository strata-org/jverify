package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.throwsOn;

/**
 * Wishlist: an unguarded cast in a per-type claim should make the cast's
 * type obligation fire -- the thrown value here is an {@code AppException},
 * never a {@code ParseError}, so the claim must FAIL verification with a
 * proof error. Today the shape does not get that far: it dies inside Strata
 * ("resolution after 'LiftImperativeExpressions' introduced this diagnostic:
 * expected '$Box', got 'bool'") before the obligation exists. Expected to
 * fail until the Strata-side lowering of `as` inside case postconditions is
 * fixed; then this pins that the obligation actually fires.
 */
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_EXCEPTIONS},
        continueOnErrors = true, exitCode = 0,
        methodsVerified = 3, // 3 implicit constructors
        methodsInvalid = 1, // castObligation(): the cast obligation must fail
        errorCount = 1)
class WishlistCastObligation {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'
    static class ParseError extends AppException {}

    static int castObligation(int x) throws AppException {
        throwsOn(x < 0).postcondition((AppException e) ->
                ((ParseError) e) instanceof ParseError);
//               ^ error: postcondition does not hold
        if (x < 0) {
            throw new AppException();
        }
        return x;
    }
}
