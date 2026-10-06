package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.3 -- Narrowing Primitive Conversion: explicit casts that may lose information
class NarrowingPrimitiveConversion_3 {
    static void run() {
       // long -> int, low 32 bits = 7
        double d = 3.99;
        int di = (int) d;
        check(di == 3);
    }
}
