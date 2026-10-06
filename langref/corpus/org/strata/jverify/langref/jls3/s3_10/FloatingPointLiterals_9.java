package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.2 -- Floating-point Literals: leading-dot form with no integer part.
class FloatingPointLiterals_9 {
    static void run() {

        double x = .5;
        check(x == 0.5);
    }
}
