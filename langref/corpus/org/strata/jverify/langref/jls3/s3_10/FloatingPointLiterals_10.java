package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.2 -- Floating-point Literals: exponent uppercase E with signed exponent.
class FloatingPointLiterals_10 {
    static void run() {

        double x = 1E-3;
        check(x == 0.001);
    }
}
