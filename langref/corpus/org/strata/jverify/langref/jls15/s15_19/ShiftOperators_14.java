package org.strata.jverify.langref.jls15.s15_19;

import static org.strata.jverify.JVerify.*;

// JLS 15.19 -- Shift Operators (<<, >>, >>>): left shift, signed right shift,
// unsigned right shift, with int/long operands and shift-distance masking.
class ShiftOperators_14 {
    static void run() {

        // The right operand may be of type long; only its low bits matter.
        long dist = 3L;
        check((1 << dist) == 8);
    }
}
