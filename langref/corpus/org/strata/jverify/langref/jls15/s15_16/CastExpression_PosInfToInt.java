package org.strata.jverify.langref.jls15.s15_16;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: narrowing conversion (double -> int) of
// positive infinity saturates to Integer.MAX_VALUE per JLS 5.1.3.
class CastExpression_PosInfToInt {
    static void run() {

        int r = (int) Double.POSITIVE_INFINITY;
        check(r == Integer.MAX_VALUE);
    }
}
