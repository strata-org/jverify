package org.strata.jverify.langref.jls15.s15_15;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.4 -- Unary Minus - applied to 0.0 yields -0.0; dividing 1.0 by -0.0
// produces negative infinity per IEEE 754.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_15.UnaryMinus_NegZeroFloat#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class UnaryMinus_NegZeroFloat {
    static void run() {

        double v = -0.0;
        double inv = 1.0 / v;
        check(inv == Double.NEGATIVE_INFINITY);
    }
}
