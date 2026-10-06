package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.2 -- Floating-Point Literals: double/float, exponent, hex float, suffixes, underscores
class FloatingPointLiterals_7 {
    static void run() {

        double under = 1_000.000_1;
        check(under == 1000.0001);
    }
}
