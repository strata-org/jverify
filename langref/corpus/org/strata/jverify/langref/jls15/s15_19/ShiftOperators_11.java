package org.strata.jverify.langref.jls15.s15_19;

import static org.strata.jverify.JVerify.*;

// JLS 15.19 -- Shift Operators (<<, >>, >>>): left shift, signed right shift,
// unsigned right shift, with int/long operands and shift-distance masking.
class ShiftOperators_11 {
    static void run() {
   // shift by 0

        // For long, the shift distance is masked with 0x3f (low 6 bits).
        check((1L << 32) == 4294967296L);
    }
}
