package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.8 -- Unboxing Conversion: wrapper reference type to corresponding primitive
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.UnboxingConversion_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class UnboxingConversion_4 {
    static void run() {

        Integer boxed = 10;        // Long -> long
        int sum = boxed + 5;
        check(sum == 15);
    }
}
