package org.strata.jverify.langref.jls4.s4_2;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.2 -- Integer Operations: binary numeric promotion widens byte to int
// before arithmetic; the result of byte + int is int.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_2.IntegerOperations_ByteArithPromotion#run")
class IntegerOperations_ByteArithPromotion {
    static void run() {

        byte b = 1;
        int r = b + 1;
        check(r == 2);
    }
}
