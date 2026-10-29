package org.strata.jverify.langref.jls15.s15_22;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.22.1 -- Integer Bitwise Operators & ^ |: bitwise AND, XOR, OR on int/long
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_22.IntegerBitwiseOperators_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class IntegerBitwiseOperators_1 {
    static void run() {

        int a = 0b1100;
        int b = 0b1010;

        // bitwise AND
        check((a & b) == 0b1000);
    }
}
