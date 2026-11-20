package org.strata.jverify.langref.jls15.s15_17;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.2 -- Division Operator /: integer division truncates toward zero,
// sign rules, long division, and floating-point division.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_17.DivisionOperator_8#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class DivisionOperator_8 {
    static void run() {

        // floating-point division
        double d = 7.0 / 2.0;
        check(d == 3.5);
    }
}
