package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.2 -- Integer Operations: arithmetic, comparison, bitwise, shift, overflow wraparound.
class IntegerOperations_20 {
    static void run() {

        // long arithmetic
        long big = 3000000000L;
        check(big * 2 == 6000000000L);
    }
}
