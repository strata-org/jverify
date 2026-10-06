package org.strata.jverify.langref.jls15.s15_18;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.2 -- Additive Operators (+ and -) for Numeric Types: binary numeric
// promotion, integer/long overflow wraparound, and floating-point addition.
class NumericAdditiveOperators_2 {
    static void run() {

        int diff = 10 - 4;
        check(diff == 6);
    }
}
