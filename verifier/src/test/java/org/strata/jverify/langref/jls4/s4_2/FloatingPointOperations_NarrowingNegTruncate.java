package org.strata.jverify.langref.jls4.s4_2;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.4 (via JLS 5.1.3) -- Narrowing a negative double to int rounds toward zero (truncates).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_2.FloatingPointOperations_NarrowingNegTruncate#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class FloatingPointOperations_NarrowingNegTruncate {
    static void run() {

        int r = (int) -3.7;
        check(r == -3);
    }
}
