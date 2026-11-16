package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * {@code equals} on a box field that was never assigned throws
 * {@code NullPointerException} in Java, so the check after it is never
 * reached. The run must not pass: on the direct Cortex translation it stops at
 * the call with no verdict.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_OTHER},
        exitCode = 0)
class InterpretBoxingNullEquals {
    static class Holder {
        Integer x;
    }

    @InterpretEntry
    static void run() {
        Holder h = new Holder();
        check(!h.x.equals(5L));
    }
}
