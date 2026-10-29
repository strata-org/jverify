package org.strata.jverify.langref.jls15.s15_18;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.2 -- Additive Operators (+ and -) for Numeric Types: binary numeric
// promotion, integer/long overflow wraparound, and floating-point addition.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_18.NumericAdditiveOperators_7#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class NumericAdditiveOperators_7 {
    static void run() {

        // floating-point addition/subtraction
        double d = 0.5 + 0.25;
        check(d == 0.75);
    }
}
