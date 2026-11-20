package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A check on what a library method computed from its own bytecode fails at the
 * check when the value is another.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_OTHER},
        exitCode = 4,
        errorCount = 1)
class InterpretLibraryCallsCheckFails {
    @InterpretEntry
    static void run() {
        int[] a = new int[3];
        java.util.Arrays.fill(a, 4);
        check(a[0] + a[1] + a[2] == 12);
        check(Math.max(a[0], 7) == 4);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
