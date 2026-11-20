package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A virtual call on a library receiver runs the override of the receiver's
 * class: a check that expects the library's method fails.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_OTHER},
        exitCode = 4,
        errorCount = 1)
class InterpretLibraryDispatchCheckFails {
    static class Money {
        final int cents;

        Money(int cents) {
            this.cents = cents;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Money && ((Money) other).cents == cents;
        }
    }

    @InterpretEntry
    static void run() {
        Object five = new Money(5);
        check(five.equals(five));
        check(!five.equals(new Money(5)));
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
