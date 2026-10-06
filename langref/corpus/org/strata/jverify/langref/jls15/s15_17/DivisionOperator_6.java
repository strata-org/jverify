package org.strata.jverify.langref.jls15.s15_17;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.2 -- Division Operator /: integer division truncates toward zero,
// sign rules, long division, and floating-point division.
class DivisionOperator_6 {
    static void run() {

        // long division
        long l = 1000000000000L / 1000L;
        check(l == 1000000000L);
    }
}
