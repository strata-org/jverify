package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.1 -- Identity Conversion: a value of a type is converted to that same type
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.IdentityConversion_2#run")
class IdentityConversion_2 {
    static void run() {
          // int -> int
        boolean b = true;
        boolean c = b;
        check(c == true);
    }
}
