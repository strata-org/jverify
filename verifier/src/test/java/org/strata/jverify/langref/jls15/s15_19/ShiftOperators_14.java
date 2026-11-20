package org.strata.jverify.langref.jls15.s15_19;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.19 -- Shift Operators (<<, >>, >>>): left shift, signed right shift,
// unsigned right shift, with int/long operands and shift-distance masking.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_19.ShiftOperators_14#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ShiftOperators_14 {
    static void run() {

        // The right operand may be of type long; only its low bits matter.
        long dist = 3L;
        check((1 << dist) == 8);
    }
}
