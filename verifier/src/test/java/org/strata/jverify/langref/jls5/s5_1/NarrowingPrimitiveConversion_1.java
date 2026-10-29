package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.3 -- Narrowing Primitive Conversion: explicit casts that may lose information
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.NarrowingPrimitiveConversion_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class NarrowingPrimitiveConversion_1 {
    static void run() {

        int i = 300;
        byte b = (byte) i;  // int -> short, 70000 - 65536 = 4464
        check(b == 44);
    }
}
