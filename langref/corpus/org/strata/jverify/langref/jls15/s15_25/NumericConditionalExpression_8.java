package org.strata.jverify.langref.jls15.s15_25;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.2 -- Numeric Conditional Expressions: ? : where both operands are numeric,
// the result type is the binary-numeric-promotion of the operand types.
class NumericConditionalExpression_8 {
    static void run() {

        // Unboxing: Integer operand unboxes to int under numeric promotion.
        Integer boxed = 42;
        int unboxed = true ? boxed : 0;
        check(unboxed == 42);
    }
}
