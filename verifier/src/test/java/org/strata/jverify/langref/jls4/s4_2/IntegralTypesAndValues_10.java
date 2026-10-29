package org.strata.jverify.langref.jls4.s4_2;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.2.1 -- Integral Types and Values: byte, short, int, long, char ranges and values.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_2.IntegralTypesAndValues_10#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class IntegralTypesAndValues_10 {
    static void run() {

        check(((char) 0) == 0);
    }
}
