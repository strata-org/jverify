package org.strata.jverify.langref.jls15.s15_18;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.2 -- Additive Operators (+ and -) for Numeric Types: binary numeric
// promotion, integer/long overflow wraparound, and floating-point addition.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_18.NumericAdditiveOperators_5#run")
class NumericAdditiveOperators_5 {
    static void run() {

        // long arithmetic
        long l = 9000000000L + 1L;
        check(l == 9000000001L);
    }
}
