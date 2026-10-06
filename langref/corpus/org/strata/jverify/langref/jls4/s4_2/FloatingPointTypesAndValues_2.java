package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.3 -- Floating-Point Types and Values: float/double values, infinities, NaN, signed zero.
class FloatingPointTypesAndValues_2 {
    static void run() {

        double d = 2.25;
        check(d == 2.25);
    }
}
