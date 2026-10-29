package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.3 -- Narrowing Primitive Conversion: explicit casts that may lose information
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.NarrowingPrimitiveConversion_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class NarrowingPrimitiveConversion_2 {
    static void run() {
      // int -> byte, 300 mod 256 = 44
        long l = 4294967296L + 7; // = 2^32 + 7
        int li = (int) l;
        check(li == 7);
    }
}
