package org.strata.jverify.langref.jls15.s15_25;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.2 -- Numeric Conditional Expressions: ? : where both operands are numeric,
// the result type is the binary-numeric-promotion of the operand types.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_25.NumericConditionalExpression_5#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class NumericConditionalExpression_5 {
    static void run() {

        // Mixed int and double: promotion -> double.
        double d = (1 == 1) ? 3 : 4.5;
        check(d == 3.0);
    }
}
