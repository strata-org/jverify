package org.strata.jverify.langref.jls15.s15_25;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.2 -- Numeric Conditional Expressions: ? : where both operands are numeric,
// the result type is the binary-numeric-promotion of the operand types.
class NumericConditionalExpression_3 {
    static void run() {

        // Mixed int and long: binary numeric promotion -> long.
        long m = true ? 1 : 2L;
        check(m == 1L);
    }
}
