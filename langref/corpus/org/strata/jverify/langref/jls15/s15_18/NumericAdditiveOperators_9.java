package org.strata.jverify.langref.jls15.s15_18;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.2 -- Additive Operators (+ and -) for Numeric Types: binary numeric
// promotion, integer/long overflow wraparound, and floating-point addition.
class NumericAdditiveOperators_9 {
    static void run() {

        // float promotion with int -> float
        float f = 1.5f + 2;
        check(f == 3.5f);
    }
}
