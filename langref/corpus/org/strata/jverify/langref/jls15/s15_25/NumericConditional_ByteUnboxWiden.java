package org.strata.jverify.langref.jls15.s15_25;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.2 -- Numeric Conditional Expression: a Byte second operand is
// unboxed to byte and then binary numeric promotion widens it to int alongside
// the int third operand.
class NumericConditional_ByteUnboxWiden {
    static void run() {

        Byte b = 10;
        int r = true ? b : 0;
        check(r == 10);
    }
}
