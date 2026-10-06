package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.8 -- Unboxing Conversion: wrapper reference type to corresponding primitive
class UnboxingConversion_2 {
    static void run() {
          // Integer -> int
        Boolean bBoxed = false;
        boolean b = bBoxed;
        check(b == false);
    }
}
