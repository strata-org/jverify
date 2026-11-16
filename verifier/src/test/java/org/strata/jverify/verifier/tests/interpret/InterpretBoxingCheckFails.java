package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A check on a box that holds another value fails at the check.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_OTHER},
        exitCode = 4,
        methodsVerified = 1,
        methodsInvalid = 1,
        methodsSkipped = 0,
        errorCount = 1)
class InterpretBoxingCheckFails {
    @InterpretEntry
    static void run() {
        Integer i = 41;
        Integer j = i + 1;
        check(j.equals(42));
        check(i.equals(42));
//      ^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
