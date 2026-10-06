package org.strata.jverify.langref.jls15.s15_15;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.5 -- Bitwise Complement ~ applied to a long: ~0L equals -1L
// (two's-complement all-ones pattern).
class BitwiseComplement_Long {
    static void run() {

        long v = ~0L;
        check(v == -1L);
    }
}
