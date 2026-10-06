package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.7 -- Boxing Conversion: primitive to corresponding wrapper reference type
class BoxingConversion_4 {
    static void run() {
      // char -> Character
        Long l = 100L;
        check(l == 100L);
    }
}
