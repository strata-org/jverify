package org.strata.jverify.langref.jls15.s15_15;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.5 -- Bitwise Complement Operator ~: ~x flips every bit; ~x == -x - 1.
class BitwiseComplementOperator_4 {
    static void run() {

        long lx = 0L;
        check(~lx == -1L);
    }
}
