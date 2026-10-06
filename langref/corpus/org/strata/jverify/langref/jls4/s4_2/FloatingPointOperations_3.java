package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.4 -- Floating-Point Operations: arithmetic, NaN propagation, infinity/zero rules, remainder.
class FloatingPointOperations_3 {
    static void run() {

        check(2.0 * 3.0 == 6.0);
    }
}
