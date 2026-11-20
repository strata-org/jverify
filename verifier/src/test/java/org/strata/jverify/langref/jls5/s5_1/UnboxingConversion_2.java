package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.8 -- Unboxing Conversion: wrapper reference type to corresponding primitive
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.UnboxingConversion_2#run")
class UnboxingConversion_2 {
    static void run() {
          // Integer -> int
        Boolean bBoxed = false;
        boolean b = bBoxed;
        check(b == false);
    }
}
