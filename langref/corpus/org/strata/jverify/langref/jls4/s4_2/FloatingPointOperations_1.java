package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.4 -- Floating-Point Operations: arithmetic, NaN propagation, infinity/zero rules, remainder.
class FloatingPointOperations_1 {
    static void run() {

        // basic arithmetic
        check(1.5 + 2.5 == 4.0);
    }
}
