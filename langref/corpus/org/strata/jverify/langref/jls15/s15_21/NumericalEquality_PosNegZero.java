package org.strata.jverify.langref.jls15.s15_21;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.1 -- Numerical Equality Operators: positive and negative zero
// compare equal under ==.
class NumericalEquality_PosNegZero {
    static void run() {

        check(0.0 == -0.0);
    }
}
