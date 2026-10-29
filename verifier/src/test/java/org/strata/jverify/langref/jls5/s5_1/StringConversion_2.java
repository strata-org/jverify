package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.11 -- String Conversion: any value convertible to String in the string concatenation context
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.StringConversion_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class StringConversion_2 {
    static void run() {

        boolean b = true;     // int -> String
        String sb = "" + b;
        check(sb.equals("true"));
    }
}
