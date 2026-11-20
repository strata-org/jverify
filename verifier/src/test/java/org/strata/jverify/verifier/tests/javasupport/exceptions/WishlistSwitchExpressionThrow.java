package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * Wishlist (deferred from phase 2): a throw as
 * a switch EXPRESSION rule body. Statement-form switches carry throw rules
 * already (SwitchDesugarer's if-cascade branches take statements), but the
 * expression form desugars to a {@code ?:} chain, which has no statement
 * position -- so the desugarer refuses and the method skips. Supporting this
 * needs a statement-form desugaring for expression switches with throwing
 * rules.
 */
@JVerifyTest(methodsVerified = 3) // sign() + 2 implicit constructors
class WishlistSwitchExpressionThrow {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'

    static int sign(int i) throws AppException {
        return switch (Integer.signum(i)) {
//                     ^ warning: missing contract for method 'signum' in class 'java.lang.Integer'
            case 1 -> 1;
            case -1 -> -1;
            case 0 -> throw new AppException();
            default -> 0;
        };
    }
}
