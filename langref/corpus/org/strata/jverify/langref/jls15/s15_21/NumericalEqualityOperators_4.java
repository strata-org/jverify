package org.strata.jverify.langref.jls15.s15_21;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.1 -- Numerical Equality Operators == and !=: binary numeric promotion then value comparison
class NumericalEqualityOperators_4 {
    static void run() {

        long l = 100L;
        check(l == 100);
    }
}
