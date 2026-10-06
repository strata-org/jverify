package org.strata.jverify.langref.jls4.s4_2;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.2 (via JLS 15.19) -- for int shifts the shift distance is masked with 0x1f,
// so 1 << 33 is 1 << 1 == 2.
class IntegerOperations_ShiftMaskInt {
    static void run() {

        int v = 1 << 33;
        check(v == 2);
    }
}
