package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.4 -- Floating-Point Operations: arithmetic, NaN propagation, infinity/zero rules, remainder.
class FloatingPointOperations_12 {
    static void run() {

        // infinity arithmetic
        double inf = 1.0 / 0.0; // inf - inf is NaN
        check(inf * 2.0 == inf);
    }
}
