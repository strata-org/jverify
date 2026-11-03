package org.strata.jverify.langref.jls15.s15_25;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.25.2 -- Numeric Conditional Expression: a Byte second operand is
// unboxed to byte and then binary numeric promotion widens it to int alongside
// the int third operand.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_25.NumericConditional_ByteUnboxWiden#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class NumericConditional_ByteUnboxWiden {
    static void run() {

        Byte b = 10;
        int r = true ? b : 0;
        check(r == 10);
    }
}
