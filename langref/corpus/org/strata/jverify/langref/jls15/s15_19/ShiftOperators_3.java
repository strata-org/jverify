package org.strata.jverify.langref.jls15.s15_19;

import static org.strata.jverify.JVerify.*;

// JLS 15.19 -- Shift Operators (<<, >>, >>>): left shift, signed right shift,
// unsigned right shift, with int/long operands and shift-distance masking.
class ShiftOperators_3 {
    static void run() {

        // Signed right shift >> : sign-extending, arithmetic.
        check((16 >> 2) == 4);
    }
}
