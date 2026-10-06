package org.strata.jverify.langref.jls15.s15_19;

import static org.strata.jverify.JVerify.*;

// JLS 15.19 -- Shift Operators (<<, >>, >>>): left shift, signed right shift,
// unsigned right shift, with int/long operands and shift-distance masking.
class ShiftOperators_13 {
    static void run() {
    // 64 & 0x3f == 0
        check((-1L >>> 60) == 0xFL);
    }
}
