package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.2 -- Floating-point Literals: uppercase F float suffix.
class FloatingPointLiterals_11 {
    static void run() {

        float x = 1.5F;
        check(x == 1.5f);
    }
}
