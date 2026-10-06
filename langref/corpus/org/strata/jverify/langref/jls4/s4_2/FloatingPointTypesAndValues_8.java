package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.3 -- Floating-Point Types and Values: float/double values, infinities, NaN, signed zero.
class FloatingPointTypesAndValues_8 {
    static void run() {

        // NaN compares unequal to everything, including itself
        double nan = 0.0 / 0.0;
        check(!(nan == nan));
    }
}
