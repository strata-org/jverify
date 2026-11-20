package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.2 -- Widening Primitive Conversion: byte->short->int->long->float->double, char->int
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.WideningPrimitiveConversion_2#run")
class WideningPrimitiveConversion_2 {
    static void run() {

        byte b = 100;
        short s = b;        // byte -> short
        int i = s;
        check(i == 100);
    }
}
