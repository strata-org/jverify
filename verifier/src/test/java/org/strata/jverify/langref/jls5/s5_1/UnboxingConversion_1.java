package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.8 -- Unboxing Conversion: wrapper reference type to corresponding primitive
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.UnboxingConversion_1#run")
class UnboxingConversion_1 {
    static void run() {

        Integer boxed = 10;
        int i = boxed;    // unboxing in arithmetic
        check(i == 10);
    }
}
