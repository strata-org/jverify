package org.strata.jverify.langref.jls15.s15_15;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.5 -- Bitwise Complement Operator ~: ~x flips every bit; ~x == -x - 1.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_15.BitwiseComplementOperator_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class BitwiseComplementOperator_1 {
    static void run() {

        int x = 5;
        int c = ~x;
        check(c == -6);
    }
}
