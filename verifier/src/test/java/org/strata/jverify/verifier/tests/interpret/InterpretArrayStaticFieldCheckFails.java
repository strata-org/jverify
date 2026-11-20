package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A check on an element of an array in a static field that holds another
 * value fails at the check.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_ARRAYS},
        exitCode = 4,
        methodsVerified = 2,
        methodsInvalid = 1,
        methodsSkipped = 0,
        errorCount = 1)
class InterpretArrayStaticFieldCheckFails {
    static final int[] SQUARES = {0, 1, 4, 9};
    static int[] buf;

    @InterpretEntry
    static void run() {
        buf = new int[2];
        buf[1] = SQUARES[3];
        check(buf[0] == 0 && SQUARES.length == 4);
        check(buf[1] == 4);
//      ^^^^^^^^^^^^^^^^^^ Error: assertion does not hold; a static field may have been changed by the static initializer of 'InterpretArrayStaticFieldCheckFails', which this method may run
    }
}
