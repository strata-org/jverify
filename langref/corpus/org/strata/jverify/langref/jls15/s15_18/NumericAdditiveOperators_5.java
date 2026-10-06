package org.strata.jverify.langref.jls15.s15_18;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.2 -- Additive Operators (+ and -) for Numeric Types: binary numeric
// promotion, integer/long overflow wraparound, and floating-point addition.
class NumericAdditiveOperators_5 {
    static void run() {

        // long arithmetic
        long l = 9000000000L + 1L;
        check(l == 9000000001L);
    }
}
