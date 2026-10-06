package org.strata.jverify.langref.jls15.s15_17;

import static org.strata.jverify.JVerify.*;

// JLS 15.17.3 -- Remainder Operator %: floating-point remainder by zero yields
// NaN rather than throwing (IEEE 754 semantics).
class Remainder_DoubleByZero {
    static void run() {

        double r = 5.0 % 0.0;
        check(Double.isNaN(r));
    }
}
