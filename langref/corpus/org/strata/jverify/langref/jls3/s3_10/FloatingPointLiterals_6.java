package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.2 -- Floating-Point Literals: double/float, exponent, hex float, suffixes, underscores
class FloatingPointLiterals_6 {
    static void run() {

        double d = 3.14;
        double dSuffix = 7.0d;
        check(dSuffix == 7.0);
    }
}
