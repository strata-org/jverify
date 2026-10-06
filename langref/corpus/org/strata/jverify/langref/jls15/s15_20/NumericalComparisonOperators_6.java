package org.strata.jverify.langref.jls15.s15_20;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.1 -- Numerical Comparison Operators < <= > >=: integer and floating-point comparisons
class NumericalComparisonOperators_6 {
    static void run() {

        int a = 3;
        int b = 5;
        check(!(a > b));
    }
}
