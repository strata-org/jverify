package org.strata.jverify.langref.jls4.s4_2;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.4 -- Floating-Point Operations: arithmetic, NaN propagation, infinity/zero rules, remainder.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_2.FloatingPointOperations_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class FloatingPointOperations_3 {
    static void run() {

        check(2.0 * 3.0 == 6.0);
    }
}
