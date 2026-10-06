package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.2 -- Integer Operations: arithmetic, comparison, bitwise, shift, overflow wraparound.
class IntegerOperations_19 {
    static void run() {

        // overflow wraps around (two's complement)
        int max = 2147483647;
        check(max + 1 == -2147483648);
    }
}
