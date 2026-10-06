package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.2 -- Floating-point Literals: positive infinity from divide-by-zero.
class FloatingPointLiterals_13 {
    static void run() {

        double inf = 1.0 / 0.0;
        check(inf == Double.POSITIVE_INFINITY);
    }
}
