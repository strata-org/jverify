package org.strata.jverify.langref.jls15.s15_20;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.1 -- Numerical Comparison Operators < <= > >=: integer and floating-point comparisons
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_20.NumericalComparisonOperators_10#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class NumericalComparisonOperators_10 {
    static void run() {

        double y = 2.25;
        check(y >= 2.25);
    }
}
