package org.strata.jverify.verifier.tests.javasupport.boxing;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * {@code equals} on a box field that was never assigned throws
 * {@code NullPointerException} in Java, so the handler runs and the check
 * fails. The run must not pass by taking the call to be false: on the direct
 * Cortex translation it stops at the call with no verdict.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_OTHER},
        exitCode = 0)
class BoxingNullEqualsUncaughtCortex {
    static class Holder {
        Integer x;
    }

    @InterpretEntry
    static void run() {
        Holder h = new Holder();
        boolean caught = false;
        try {
            h.x.equals(5L);
        } catch (NullPointerException e) {
            caught = true;
        }
        check(!caught);
    }
}
