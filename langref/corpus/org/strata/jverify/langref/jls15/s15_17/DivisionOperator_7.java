package org.strata.jverify.langref.jls15.s15_17;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.2 -- Division Operator /: integer division truncates toward zero,
// sign rules, long division, and floating-point division.
class DivisionOperator_7 {
    static void run() {

        // Overflow special case: Integer.MIN_VALUE / -1 overflows back to MIN_VALUE
        int min = -2147483648;
        check(min / (-1) == -2147483648);
    }
}
