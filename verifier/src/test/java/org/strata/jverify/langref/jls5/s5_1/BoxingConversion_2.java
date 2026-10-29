package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.7 -- Boxing Conversion: primitive to corresponding wrapper reference type
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.BoxingConversion_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class BoxingConversion_2 {
    static void run() {
         // int -> Integer
        Boolean b = true;
        check(b);
    }
}
