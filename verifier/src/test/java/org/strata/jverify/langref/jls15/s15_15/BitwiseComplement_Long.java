package org.strata.jverify.langref.jls15.s15_15;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.5 -- Bitwise Complement ~ applied to a long: ~0L equals -1L
// (two's-complement all-ones pattern).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_15.BitwiseComplement_Long#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class BitwiseComplement_Long {
    static void run() {

        long v = ~0L;
        check(v == -1L);
    }
}
