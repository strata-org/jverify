package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.2 -- Floating-Point Literals: double/float, exponent, hex float, suffixes, underscores
class FloatingPointLiterals_4 {
    static void run() {

        double trail = 5.;
        check(trail == 5.0);
    }
}
