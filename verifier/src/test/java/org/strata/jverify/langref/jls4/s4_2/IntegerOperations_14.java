package org.strata.jverify.langref.jls4.s4_2;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.2 -- Integer Operations: arithmetic, comparison, bitwise, shift, overflow wraparound.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_2.IntegerOperations_14#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class IntegerOperations_14 {
    static void run() {

        check((0b1100 ^ 0b1010) == 0b0110);
    }
}
