package org.strata.jverify.langref.jls15.s15_25;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.2 -- Numeric Conditional Expressions: ? : where both operands are numeric,
// the result type is the binary-numeric-promotion of the operand types.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_25.NumericConditionalExpression_1#run")
class NumericConditionalExpression_1 {
    static void run() {

        // Same-type operands: result is that type.
        int x = (5 > 3) ? 10 : 20;
        check(x == 10);
    }
}
