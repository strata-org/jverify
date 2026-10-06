package org.strata.jverify.langref.jls15.s15_17;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.1 -- Multiplication Operator *: integer and floating-point multiplication,
// overflow wrap-around, and sign rules.
class MultiplicationOperator_7 {
    static void run() {

        // multiplication by zero
        check(0 * 12345 == 0);
    }
}
