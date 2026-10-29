package org.strata.jverify.langref.jls11.s11_3;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.20.2 -- finally runs even when the try body completes abruptly by
// throwing an exception; the exception is caught, and finally still executes.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_3.Finally_OnException#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Finally_OnException {
    static void run() {
        int[] side = new int[1];
        try {
            throw new RuntimeException("boom");
        } catch (RuntimeException e) {
            side[0] += 1;
        } finally {
            side[0] += 10;
        }
        check(side[0] == 11);
    }
}
