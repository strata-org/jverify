package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.11 -- String Conversion: any value convertible to String in the string concatenation context
class StringConversion_4 {
    static void run() {
     // char -> String
        String sn = "" + (Object) null;
        check(sn.equals("null"));
    }
}
