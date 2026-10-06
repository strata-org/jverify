package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.4 -- Floating-Point Operations: arithmetic, NaN propagation, infinity/zero rules, remainder.
class FloatingPointOperations_14 {
    static void run() {

        // float arithmetic stays float
        float ff = 0.5f + 0.25f;
        check(ff == 0.75f);
    }
}
