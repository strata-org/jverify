package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.3 -- Floating-Point Types and Values: float/double values, infinities, NaN, signed zero.
class FloatingPointTypesAndValues_1 {
    static void run() {

        float f = 1.5f;
        check(f == 1.5f);
    }
}
