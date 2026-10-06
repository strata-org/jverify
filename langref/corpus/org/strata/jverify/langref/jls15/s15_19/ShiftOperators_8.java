package org.strata.jverify.langref.jls15.s15_19;

import static org.strata.jverify.JVerify.*;

// JLS 15.19 -- Shift Operators (<<, >>, >>>): left shift, signed right shift,
// unsigned right shift, with int/long operands and shift-distance masking.
class ShiftOperators_8 {
    static void run() {

        // For int, the shift distance is masked with 0x1f (low 5 bits).
        check((1 << 32) == 1);
    }
}
