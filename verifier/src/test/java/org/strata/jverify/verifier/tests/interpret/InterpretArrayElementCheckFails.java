package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A check on an element that holds another value fails at the check.
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
class InterpretArrayElementCheckFails {
    @InterpretEntry
    static void run() {
        int[] a = new int[2];
        a[1] = 4;
        check(a[0] == 0);
        check(a[1] == 5);
//      ^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
