package org.strata.jverify.langref.jls15.s15_19;

import static org.strata.jverify.JVerify.*;

// JLS 15.19 -- Shift Operators (<<, >>, >>>): left shift, signed right shift,
// unsigned right shift, with int/long operands and shift-distance masking.
class ShiftOperators_16 {
    static void run() {

        // Difference between >> and >>> on a negative value.
        int neg = -8;
        check((neg >>> 1) == 0x7FFFFFFC);
    }
}
