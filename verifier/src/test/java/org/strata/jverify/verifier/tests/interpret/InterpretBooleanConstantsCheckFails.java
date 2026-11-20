package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * {@code Boolean.FALSE} holds false: a check that it holds true fails at the
 * check.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_OTHER},
        exitCode = 4,
        errorCount = 1)
class InterpretBooleanConstantsCheckFails {
    @InterpretEntry
    static void run() {
        check(Boolean.TRUE.booleanValue());
        check(Boolean.FALSE.booleanValue());
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
