package org.strata.jverify.langref.jls15.s15_25;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.2 -- Numeric Conditional Expressions: ? : where both operands are numeric,
// the result type is the binary-numeric-promotion of the operand types.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_25.NumericConditionalExpression_8#run")
class NumericConditionalExpression_8 {
    static void run() {

        // Unboxing: Integer operand unboxes to int under numeric promotion.
        Integer boxed = 42;
        int unboxed = true ? boxed : 0;
        check(unboxed == 42);
    }
}
