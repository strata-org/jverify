package org.strata.jverify.langref.jls15.s15_16;

import static org.strata.jverify.JVerify.*;

// JLS 15.16 -- Cast Expressions: narrowing conversion (double -> int) of NaN
// produces 0 per JLS 5.1.3.
class CastExpression_NaNToInt {
    static void run() {

        int r = (int) Double.NaN;
        check(r == 0);
    }
}
