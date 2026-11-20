package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.3 -- Narrowing Primitive Conversion: explicit casts that may lose information
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.NarrowingPrimitiveConversion_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class NarrowingPrimitiveConversion_4 {
    static void run() {
       // double -> int, truncates to 3
        int big = 70000;
        short sh = (short) big;
        check(sh == 4464);
    }
}
