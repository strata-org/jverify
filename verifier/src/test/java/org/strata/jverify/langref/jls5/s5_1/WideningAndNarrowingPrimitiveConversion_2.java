package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.4 -- Widening and Narrowing Primitive Conversion: byte->char widens to int then narrows to char
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.WideningAndNarrowingPrimitiveConversion_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class WideningAndNarrowingPrimitiveConversion_2 {
    static void run() {
      // byte -> char: sign-extend to int, then narrow to char
        char c2 = (char) -1;
        check((int) c2 == 65535);
    }
}
