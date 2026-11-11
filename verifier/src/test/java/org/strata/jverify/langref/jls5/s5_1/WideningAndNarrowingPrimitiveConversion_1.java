package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.4 -- Widening and Narrowing Primitive Conversion: byte->char widens to int then narrows to char
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.WideningAndNarrowingPrimitiveConversion_1#run")
class WideningAndNarrowingPrimitiveConversion_1 {
    static void run() {

        byte b = 65;
        char c = (char) b;    // -1 narrows to 0xFFFF
        check(c == 'A');
    }
}
