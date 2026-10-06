package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.3 -- Floating-Point Types and Values: float/double values, infinities, NaN, signed zero.
class FloatingPointTypesAndValues_11 {
    static void run() {

        // signed zero: +0.0 == -0.0 but distinguishable by division
        double posZero = 0.0;
        double negZero = -0.0;
        check(posZero == negZero);
    }
}
