package org.strata.jverify.verifier.tests.javasupport.exceptions;

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
@JVerifyTest(continueOnErrors = true,
        exitCode = 4, // the future obligation is a verify-path diagnostic
        methodsVerified = 3, // 3 implicit constructors
        methodsInvalid = 1, // castObligation(): the cast obligation must fail
        errorCount = 2)
class WishlistCastObligation {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'
    static class ParseError extends AppException {}

    static int castObligation(int x) throws AppException {
        throwsOn(x < 0).postcondition((AppException e) ->
                ((ParseError) e) instanceof ParseError);
//               ^ Error: precondition could not be proved
//               ^ Error: postcondition could not be proved
        if (x < 0) {
            throw new AppException();
        }
        return x;
    }
}
