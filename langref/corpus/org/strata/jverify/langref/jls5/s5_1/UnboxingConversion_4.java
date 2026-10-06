package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.8 -- Unboxing Conversion: wrapper reference type to corresponding primitive
class UnboxingConversion_4 {
    static void run() {

        Integer boxed = 10;        // Long -> long
        int sum = boxed + 5;
        check(sum == 15);
    }
}
