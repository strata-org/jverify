package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.2 -- Floating-point Literals: NaN from 0.0/0.0.
class FloatingPointLiterals_14 {
    static void run() {

        double nan = 0.0 / 0.0;
        check(Double.isNaN(nan));
    }
}
