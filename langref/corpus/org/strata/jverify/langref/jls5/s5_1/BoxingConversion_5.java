package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.7 -- Boxing Conversion: primitive to corresponding wrapper reference type
class BoxingConversion_5 {
    static void run() {
          // long -> Long
        Double d = 3.5;
        check(d == 3.5);
    }
}
