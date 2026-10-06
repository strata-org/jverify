package org.strata.jverify.langref.jls15.s15_25;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.2 -- Numeric Conditional Expressions: ? : where both operands are numeric,
// the result type is the binary-numeric-promotion of the operand types.
class NumericConditionalExpression_2 {
    static void run() {

        int y = (5 < 3) ? 10 : 20;
        check(y == 20);
    }
}
