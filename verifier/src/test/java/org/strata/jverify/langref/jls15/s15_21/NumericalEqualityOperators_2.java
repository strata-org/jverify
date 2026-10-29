package org.strata.jverify.langref.jls15.s15_21;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.1 -- Numerical Equality Operators == and !=: binary numeric promotion then value comparison
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_21.NumericalEqualityOperators_2#run")
class NumericalEqualityOperators_2 {
    static void run() {

        int a = 2 + 3;
        int b = 5;
        check(!(a != b));
    }
}
