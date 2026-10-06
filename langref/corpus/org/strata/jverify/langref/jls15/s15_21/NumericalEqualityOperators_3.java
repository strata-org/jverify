package org.strata.jverify.langref.jls15.s15_21;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.1 -- Numerical Equality Operators == and !=: binary numeric promotion then value comparison
class NumericalEqualityOperators_3 {
    static void run() {

        // Binary numeric promotion: int and double compared as double
        int i = 7;
        double d = 7.0;
        check(i == d);
    }
}
