package org.strata.jverify.langref.jls15.s15_25;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.2 -- Numeric Conditional Expressions: ? : where both operands are numeric,
// the result type is the binary-numeric-promotion of the operand types.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_25.NumericConditionalExpression_6#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class NumericConditionalExpression_6 {
    static void run() {

        // byte and short with constant operands representable in the narrower type.
        // Special rule: if one operand is byte and the other a constant int representable
        // as byte, the result type is byte (here we exercise short similarly).
        short s = true ? (short) 7 : (short) 9;
        check(s == 7);
    }
}
