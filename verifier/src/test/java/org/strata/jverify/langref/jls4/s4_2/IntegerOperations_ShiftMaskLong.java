package org.strata.jverify.langref.jls4.s4_2;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.2 (via JLS 15.19) -- for long shifts the shift distance is masked with 0x3f,
// so 1L << 65 is 1L << 1 == 2L.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_2.IntegerOperations_ShiftMaskLong#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class IntegerOperations_ShiftMaskLong {
    static void run() {

        long v = 1L << 65;
        check(v == 2L);
    }
}
