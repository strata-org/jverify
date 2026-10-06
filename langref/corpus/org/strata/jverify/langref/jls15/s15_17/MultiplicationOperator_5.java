package org.strata.jverify.langref.jls15.s15_17;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.1 -- Multiplication Operator *: integer and floating-point multiplication,
// overflow wrap-around, and sign rules.
class MultiplicationOperator_5 {
    static void run() {

        // int overflow wraps around (does not throw)
        int big = 100000 * 100000;
        check(big == 1410065408);
    }
}
