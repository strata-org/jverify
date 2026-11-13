package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Each array type is its own type below {@code Object}: an {@code int[]} is an
 * instance of {@code int[]} and of no other array type or class, a cast to its
 * own type gives the same array, and a cast to another array type throws
 * {@code ClassCastException}.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_ARRAYS},
        exitCode = 0,
        methodsVerified = 3,
        methodsInvalid = 0,
        methodsSkipped = 0,
        errorCount = 0)
class InterpretArrayInstanceOf {
    static class Other {
    }

    @InterpretEntry
    static void run() {
        Object ints = new int[1];
        check(ints instanceof int[]);
        check(!(ints instanceof long[]));
        check(!(ints instanceof byte[]));
        check(!(ints instanceof Other));

        Object bytes = new byte[1];
        check(bytes instanceof byte[]);
        check(!(bytes instanceof boolean[]));
        check(!(bytes instanceof int[]));

        Object plain = new Other();
        check(!(plain instanceof int[]));

        int[] back = (int[]) ints;
        back[0] = 3;
        check(((int[]) ints)[0] == 3);

        boolean caught = false;
        try {
            long[] wrong = (long[]) ints;
            check(false);
        } catch (ClassCastException e) {
            caught = true;
        }
        check(caught);
    }
}
