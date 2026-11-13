package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * An allocation with a negative length, with no handler, ends the run with the
 * uncaught {@code NegativeArraySizeException} reported at the allocation.
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
class InterpretArrayNegativeSize {
    @InterpretEntry
    static void run() {
        int length = 2;
        length -= 3;
        long[] a = new long[length];
//                 ^^^^^^^^^^^^^^^^ Error: uncaught exception 'java.lang.NegativeArraySizeException' is not thrown does not hold
    }
}
