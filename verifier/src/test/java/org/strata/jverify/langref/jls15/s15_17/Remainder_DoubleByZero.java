package org.strata.jverify.langref.jls15.s15_17;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.3 -- Remainder Operator %: floating-point remainder by zero yields
// NaN rather than throwing (IEEE 754 semantics).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_17.Remainder_DoubleByZero#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Remainder_DoubleByZero {
    static void run() {

        double r = 5.0 % 0.0;
        check(Double.isNaN(r));
    }
}
