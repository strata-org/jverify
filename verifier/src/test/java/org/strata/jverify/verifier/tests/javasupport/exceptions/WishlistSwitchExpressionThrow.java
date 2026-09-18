package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * Wishlist (docs/exceptions-design.md, deferred from Phasing §2): a throw as
 * a switch EXPRESSION rule body. Statement-form switches carry throw rules
 * already (SwitchDesugarer's if-cascade branches take statements), but the
 * expression form desugars to a {@code ?:} chain, which has no statement
 * position -- so the desugarer refuses and the method skips. Supporting this
 * needs a statement-form desugaring for expression switches with throwing
 * rules.
 */
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_EXCEPTIONS},
        methodsVerified = 3) // sign() + 2 implicit constructors
class WishlistSwitchExpressionThrow {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'

    static int sign(int i) throws AppException {
        return switch (Integer.signum(i)) {
            case 1 -> 1;
            case -1 -> -1;
            case 0 -> throw new AppException();
            default -> 0;
        };
    }
}
