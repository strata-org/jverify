package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.4 -- Floating-Point Operations: arithmetic, NaN propagation, infinity/zero rules, remainder.
class FloatingPointOperations_2 {
    static void run() {

        check(5.0 - 1.5 == 3.5);
    }
}
