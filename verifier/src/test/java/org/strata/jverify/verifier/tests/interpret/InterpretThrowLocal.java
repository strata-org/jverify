package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * An exception thrown from a local variable is reported with the class of the
 * object that was stored in it, {@code IllegalStateException}, not with the
 * local's declared class, {@code RuntimeException}.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_OTHER},
        exitCode = 4,
        errorCount = 1)
class InterpretThrowLocal {
    @InterpretEntry
    static void run() {
        RuntimeException e = new IllegalStateException();
        throw e;
//      ^^^^^^^^ Error: uncaught exception 'java.lang.IllegalStateException' is not thrown does not hold
    }
}
