package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.4 -- Floating-Point Operations: arithmetic, NaN propagation, infinity/zero rules, remainder.
class FloatingPointOperations_9 {
    static void run() {

        // any operation with NaN yields NaN
        double nan = 0.0 / 0.0;
        double r = nan + 1.0;
        check(r != r);
    }
}
