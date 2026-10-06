package org.strata.jverify.langref.jls15.s15_17;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.2 -- Division Operator /: integer division truncates toward zero,
// sign rules, long division, and floating-point division.
class DivisionOperator_9 {
    static void run() {

        // floating-point division by zero yields infinity (no exception)
        double inf = 1.0 / 0.0;
        check(inf > 1.0e308);
    }
}
