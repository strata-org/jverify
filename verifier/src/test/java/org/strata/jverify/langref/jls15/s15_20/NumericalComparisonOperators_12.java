package org.strata.jverify.langref.jls15.s15_20;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.1 -- Numerical Comparison Operators < <= > >=: integer and floating-point comparisons
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_20.NumericalComparisonOperators_12#run")
class NumericalComparisonOperators_12 {
    static void run() {

        long p = 100L;
        long q = 100L;
        check(p >= q);
    }
}
