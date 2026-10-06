package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.1 -- Identity Conversion: a value of a type is converted to that same type
class IdentityConversion_1 {
    static void run() {

        int i = 42;
        int j = i;       // String -> String
        check(j == 42);
    }
}
