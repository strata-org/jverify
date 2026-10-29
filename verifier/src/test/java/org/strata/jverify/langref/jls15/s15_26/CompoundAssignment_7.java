package org.strata.jverify.langref.jls15.s15_26;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.26.2 -- Compound Assignment Operators (op=): E1 op= E2 is equivalent
// to E1 = (T)((E1) op (E2)) where T is the type of E1, with E1 evaluated once
// and an implicit narrowing cast applied to the result.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_26.CompoundAssignment_7#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class CompoundAssignment_7 {
    static void run() {

        int bits = 0b1100;
        bits <<= 2;

        bits >>= 1;
        check(bits == 0b11000);
    }
}
