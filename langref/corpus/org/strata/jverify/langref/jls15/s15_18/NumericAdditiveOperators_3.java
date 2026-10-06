package org.strata.jverify.langref.jls15.s15_18;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.2 -- Additive Operators (+ and -) for Numeric Types: binary numeric
// promotion, integer/long overflow wraparound, and floating-point addition.
class NumericAdditiveOperators_3 {
    static void run() {

        // binary numeric promotion: byte + byte -> int
        byte b1 = 100;
        byte b2 = 27;
        int promoted = b1 + b2;
        check(promoted == 127);
    }
}
