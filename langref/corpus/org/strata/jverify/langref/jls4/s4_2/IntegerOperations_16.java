package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.2 -- Integer Operations: arithmetic, comparison, bitwise, shift, overflow wraparound.
class IntegerOperations_16 {
    static void run() {

        // shift
        check((1 << 4) == 16);
    }
}
