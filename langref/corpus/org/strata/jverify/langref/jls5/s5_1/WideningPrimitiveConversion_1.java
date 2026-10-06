package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.2 -- Widening Primitive Conversion: byte->short->int->long->float->double, char->int
class WideningPrimitiveConversion_1 {
    static void run() {

        byte b = 100;
        short s = b;         // char -> int
        check(s == 100);
    }
}
