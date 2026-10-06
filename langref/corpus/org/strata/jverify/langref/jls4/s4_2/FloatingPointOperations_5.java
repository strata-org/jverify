package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.4 -- Floating-Point Operations: arithmetic, NaN propagation, infinity/zero rules, remainder.
class FloatingPointOperations_5 {
    static void run() {

        // floating-point remainder (truncating, not IEEE remainder)
        check(5.0 % 3.0 == 2.0);
    }
}
