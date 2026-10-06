package org.strata.jverify.langref.jls15.s15_19;

import static org.strata.jverify.JVerify.*;

// JLS 15.19 -- Shift Operators (<<, >>, >>>): left shift, signed right shift,
// unsigned right shift, with int/long operands and shift-distance masking.
class ShiftOperators_6 {
    static void run() {

        // Unsigned right shift >>> : zero-fill from the left.
        check((-1 >>> 28) == 0xF);
    }
}
