package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.4 -- Widening and Narrowing Primitive Conversion: byte->char widens to int then narrows to char
class WideningAndNarrowingPrimitiveConversion_1 {
    static void run() {

        byte b = 65;
        char c = (char) b;    // -1 narrows to 0xFFFF
        check(c == 'A');
    }
}
