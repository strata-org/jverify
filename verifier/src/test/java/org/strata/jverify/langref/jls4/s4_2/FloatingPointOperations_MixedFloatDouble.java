package org.strata.jverify.langref.jls4.s4_2;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.4 -- Floating-point Operations: binary numeric promotion widens float to double
// when the other operand is double; the result type is double.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_2.FloatingPointOperations_MixedFloatDouble#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class FloatingPointOperations_MixedFloatDouble {
    static void run() {

        float f = 1.0f;
        double d = 2.0;
        double r = f + d;
        check(r == 3.0);
    }
}
