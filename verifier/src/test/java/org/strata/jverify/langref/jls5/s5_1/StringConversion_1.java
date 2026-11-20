package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.11 -- String Conversion: any value convertible to String in the string concatenation context
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.StringConversion_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class StringConversion_1 {
    static void run() {

        int i = 5;
        String si = "" + i; // null reference -> "null"
        check(si.equals("5"));
    }
}
