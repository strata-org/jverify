package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.2 -- Floating-point Literals: hexadecimal float literal with negative binary exponent and f suffix.
class FloatingPointLiterals_15 {
    static void run() {

        float x = 0x1.0p-1f;
        check(x == 0.5f);
    }
}
