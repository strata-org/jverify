package org.strata.jverify.langref.jls15.s15_26;

import static org.strata.jverify.JVerify.*;

// JLS 15.26.2 -- Compound Assignment Operators (op=): E1 op= E2 is equivalent
// to E1 = (T)((E1) op (E2)) where T is the type of E1, with E1 evaluated once
// and an implicit narrowing cast applied to the result.
class CompoundAssignment_10 {
    static void run() {

        int bits = 0b1100;
        bits <<= 2;

        bits >>= 1;

        bits &= 0b10000;

        bits |= 0b00001;
        check(bits == 0b10001);
    }
}
