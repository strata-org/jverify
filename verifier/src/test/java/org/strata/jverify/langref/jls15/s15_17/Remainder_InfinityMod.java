package org.strata.jverify.langref.jls15.s15_17;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.3 -- Remainder Operator %: an infinite dividend with a finite,
// nonzero divisor produces NaN.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_17.Remainder_InfinityMod#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class Remainder_InfinityMod {
    static void run() {

        double r = Double.POSITIVE_INFINITY % 1.0;
        check(Double.isNaN(r));
    }
}
