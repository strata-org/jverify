package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.implies;
import static org.strata.jverify.JVerify.throwsOn;

/**
 * Wishlist (docs/exceptions-design.md, Decision 6): exceptions carrying data
 * through their constructor. {@code new E(args)} discards constructor
 * arguments today (JavaToLaurelCompiler's JCNewClass case), so a case
 * postcondition about a field the constructor set is unprovable.
 * Blocked on constructor-argument translation; until then the workaround is
 * field writes after {@code new} -- which this test deliberately does NOT
 * use, so it starts passing exactly when the constructor path works.
 */
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_EXCEPTIONS},
        methodsVerified = 3) // value(), IndexError(int), and the test class's implicit constructor
class WishlistConstructorData {
    static class IndexError extends Exception {
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'
        final int badIndex;

        IndexError(int badIndex) {
            this.badIndex = badIndex;
        }
    }

    static int value(int i, int len) throws IndexError {
        throwsOn(i < 0 || i >= len).postcondition((IndexError e) ->
                implies(e instanceof IndexError, ((IndexError) e).badIndex == i));
        if (i < 0 || i >= len) {
            throw new IndexError(i);
        }
        return i;
    }
}
