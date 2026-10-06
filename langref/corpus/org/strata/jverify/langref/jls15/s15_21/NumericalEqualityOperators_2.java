package org.strata.jverify.langref.jls15.s15_21;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.1 -- Numerical Equality Operators == and !=: binary numeric promotion then value comparison
class NumericalEqualityOperators_2 {
    static void run() {

        int a = 2 + 3;
        int b = 5;
        check(!(a != b));
    }
}
