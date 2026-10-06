package org.strata.jverify.langref.jls15.s15_20;

import static org.strata.jverify.JVerify.*;

// JLS 15.20.1 -- Numerical Comparison Operators: every ordered comparison with
// NaN as either operand yields false.
class NumericalComparison_NaNAllFalse {
    static void run() {

        boolean r = (Double.NaN < 1.0) || (Double.NaN >= 1.0) || (Double.NaN <= 1.0);
        check(!r);
    }
}
