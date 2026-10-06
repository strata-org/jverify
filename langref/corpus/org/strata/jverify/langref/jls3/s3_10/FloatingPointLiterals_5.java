package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.2 -- Floating-Point Literals: double/float, exponent, hex float, suffixes, underscores
class FloatingPointLiterals_5 {
    static void run() {

        float f = 2.5f;
        check(f == 2.5f);
    }
}
