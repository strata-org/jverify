package org.strata.jverify.langref.jls15.s15_20;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.1 -- Numerical Comparison Operators < <= > >=: integer and floating-point comparisons
class NumericalComparisonOperators_9 {
    static void run() {

        double x = 1.5;
        check(x <= 1.5);
    }
}
