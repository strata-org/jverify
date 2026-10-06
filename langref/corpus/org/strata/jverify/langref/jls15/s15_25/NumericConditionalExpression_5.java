package org.strata.jverify.langref.jls15.s15_25;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.2 -- Numeric Conditional Expressions: ? : where both operands are numeric,
// the result type is the binary-numeric-promotion of the operand types.
class NumericConditionalExpression_5 {
    static void run() {

        // Mixed int and double: promotion -> double.
        double d = (1 == 1) ? 3 : 4.5;
        check(d == 3.0);
    }
}
