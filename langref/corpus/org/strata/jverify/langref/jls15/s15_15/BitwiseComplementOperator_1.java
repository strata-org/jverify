package org.strata.jverify.langref.jls15.s15_15;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.5 -- Bitwise Complement Operator ~: ~x flips every bit; ~x == -x - 1.
class BitwiseComplementOperator_1 {
    static void run() {

        int x = 5;
        int c = ~x;
        check(c == -6);
    }
}
