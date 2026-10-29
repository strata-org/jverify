package org.strata.jverify.langref.jls5.s5_1;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.2 -- Widening Primitive Conversion: byte->short->int->long->float->double, char->int
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_1.WideningPrimitiveConversion_6#run")
class WideningPrimitiveConversion_6 {
    static void run() {
       // float -> double
        char c = 'A';
        int ci = c;
        check(ci == 65);
    }
}
