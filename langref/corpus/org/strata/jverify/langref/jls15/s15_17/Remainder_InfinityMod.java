package org.strata.jverify.langref.jls15.s15_17;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.3 -- Remainder Operator %: an infinite dividend with a finite,
// nonzero divisor produces NaN.
class Remainder_InfinityMod {
    static void run() {

        double r = Double.POSITIVE_INFINITY % 1.0;
        check(Double.isNaN(r));
    }
}
