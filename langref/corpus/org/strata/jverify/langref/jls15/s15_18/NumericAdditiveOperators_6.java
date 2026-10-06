package org.strata.jverify.langref.jls15.s15_18;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.2 -- Additive Operators (+ and -) for Numeric Types: binary numeric
// promotion, integer/long overflow wraparound, and floating-point addition.
class NumericAdditiveOperators_6 {
    static void run() {

        // mixed int + long -> long
        int i = 5;
        long mixed = i + 10L;
        check(mixed == 15L);
    }
}
