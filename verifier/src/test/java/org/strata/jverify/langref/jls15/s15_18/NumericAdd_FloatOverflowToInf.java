package org.strata.jverify.langref.jls15.s15_18;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.2 -- Numeric Additive Operators: float overflow rounds to
// positive infinity (IEEE 754).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_18.NumericAdd_FloatOverflowToInf#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class NumericAdd_FloatOverflowToInf {
    static void run() {

        float v = Float.MAX_VALUE + Float.MAX_VALUE;
        check(Float.isInfinite(v) && v > 0);
    }
}
