package org.strata.jverify.langref.jls15.s15_18;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.2 -- Additive Operators (+ and -) for Numeric Types: binary numeric
// promotion, integer/long overflow wraparound, and floating-point addition.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_18.NumericAdditiveOperators_2#run")
class NumericAdditiveOperators_2 {
    static void run() {

        int diff = 10 - 4;
        check(diff == 6);
    }
}
