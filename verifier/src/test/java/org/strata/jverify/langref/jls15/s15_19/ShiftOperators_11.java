package org.strata.jverify.langref.jls15.s15_19;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.19 -- Shift Operators (<<, >>, >>>): left shift, signed right shift,
// unsigned right shift, with int/long operands and shift-distance masking.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_19.ShiftOperators_11#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ShiftOperators_11 {
    static void run() {
   // shift by 0

        // For long, the shift distance is masked with 0x3f (low 6 bits).
        check((1L << 32) == 4294967296L);
    }
}
