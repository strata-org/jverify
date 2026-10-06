package org.strata.jverify.langref.jls15.s15_25;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.2 -- Numeric Conditional Expressions: ? : where both operands are numeric,
// the result type is the binary-numeric-promotion of the operand types.
class NumericConditionalExpression_6 {
    static void run() {

        // byte and short with constant operands representable in the narrower type.
        // Special rule: if one operand is byte and the other a constant int representable
        // as byte, the result type is byte (here we exercise short similarly).
        short s = true ? (short) 7 : (short) 9;
        check(s == 7);
    }
}
