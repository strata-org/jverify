package org.strata.jverify.langref.jls15.s15_20;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.1 -- Numerical Comparison Operators < <= > >=: integer and floating-point comparisons
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_20.NumericalComparisonOperators_5#run")
class NumericalComparisonOperators_5 {
    static void run() {

        int b = 5;
        check(b >= b);
    }
}
