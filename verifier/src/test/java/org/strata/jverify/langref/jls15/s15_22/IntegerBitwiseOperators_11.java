package org.strata.jverify.langref.jls15.s15_22;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.22.1 -- Integer Bitwise Operators & ^ |: bitwise AND, XOR, OR on int/long
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_22.IntegerBitwiseOperators_11#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class IntegerBitwiseOperators_11 {
    static void run() {

        check((-1 | 0) == -1);
    }
}
