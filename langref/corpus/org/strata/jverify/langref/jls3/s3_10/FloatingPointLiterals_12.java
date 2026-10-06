package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.2 -- Floating-point Literals: uppercase D double suffix.
class FloatingPointLiterals_12 {
    static void run() {

        double x = 1.5D;
        check(x == 1.5);
    }
}
