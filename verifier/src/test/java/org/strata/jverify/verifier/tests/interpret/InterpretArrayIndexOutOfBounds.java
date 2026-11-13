package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A store at the length, with no handler, ends the run with the uncaught
 * {@code ArrayIndexOutOfBoundsException} reported at the store.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_ARRAYS},
        exitCode = 4,
        methodsVerified = 1,
        methodsInvalid = 1,
        methodsSkipped = 0,
        errorCount = 1)
class InterpretArrayIndexOutOfBounds {
    @InterpretEntry
    static void run() {
        int[] a = new int[2];
        a[1] = 1;
        a[2] = 1;
//      ^^^^^^^^ Error: uncaught exception 'java.lang.ArrayIndexOutOfBoundsException' is not thrown does not hold
    }
}
