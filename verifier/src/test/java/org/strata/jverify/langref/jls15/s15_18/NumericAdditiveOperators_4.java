package org.strata.jverify.langref.jls15.s15_18;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.2 -- Additive Operators (+ and -) for Numeric Types: binary numeric
// promotion, integer/long overflow wraparound, and floating-point addition.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_18.NumericAdditiveOperators_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_ASSERT_FAILED})
class NumericAdditiveOperators_4 {
    static void run() {

        // int overflow wraps around (no exception)
        int max = 2147483647;
        check(max + 1 == -2147483648);
    }
}
