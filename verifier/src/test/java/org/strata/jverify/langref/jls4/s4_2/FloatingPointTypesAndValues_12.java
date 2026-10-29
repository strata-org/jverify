package org.strata.jverify.langref.jls4.s4_2;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.3 -- Floating-Point Types and Values: float/double values, infinities, NaN, signed zero.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_2.FloatingPointTypesAndValues_12#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class FloatingPointTypesAndValues_12 {
    static void run() {

        // signed zero: +0.0 == -0.0 but distinguishable by division
        double posZero = 0.0;
        check((1.0 / posZero) == Double.POSITIVE_INFINITY);
    }
}
