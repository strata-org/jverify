package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.2 -- Integer Operations: arithmetic, comparison, bitwise, shift, overflow wraparound.
class IntegerOperations_12 {
    static void run() {

        // bitwise
        check((0b1100 & 0b1010) == 0b1000);
    }
}
