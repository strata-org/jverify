package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A virtual call whose static target is a library method runs the method of
 * the receiver's class: {@code Object.equals} from its bytecode for an
 * {@code Object}, the override for an object of a program class that has one.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_OTHER},
        exitCode = 0,
        errorCount = 0)
class InterpretLibraryDispatch {
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
        Object plain = new Object();
        Object five = new Money(5);
        check(plain.equals(plain));
        check(!plain.equals(five));
        check(five.equals(new Money(5)));
        check(!five.equals(new Money(6)));
        check(!five.equals(plain));
    }
}
