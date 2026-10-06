package org.strata.jverify.langref.jls15.s15_17;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.1 -- Multiplication Operator *: integer and floating-point multiplication,
// overflow wrap-around, and sign rules.
class MultiplicationOperator_3 {
    static void run() {

        check((-3) * (-4) == 12);
    }
}
