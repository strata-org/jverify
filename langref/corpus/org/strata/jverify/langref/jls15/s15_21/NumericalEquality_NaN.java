package org.strata.jverify.langref.jls15.s15_21;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.1 -- Numerical Equality Operators: NaN is never equal to any value,
// including itself, per IEEE 754.
class NumericalEquality_NaN {
    static void run() {

        check(Double.NaN != Double.NaN);
    }
}
