package org.strata.jverify.langref.jls15.s15_25;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.2 -- Numeric Conditional Expressions: ? : where both operands are numeric,
// the result type is the binary-numeric-promotion of the operand types.
class NumericConditionalExpression_7 {
    static void run() {

        // Nested numeric conditional.
        int k = 2;
        int sign = (k > 0) ? 1 : ((k < 0) ? -1 : 0);
        check(sign == 1);
    }
}
