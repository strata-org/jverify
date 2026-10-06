package org.strata.jverify.langref.jls15.s15_18;

import static org.strata.jverify.JVerify.*;

// JLS 15.18.2 -- Numeric Additive Operators: float overflow rounds to
// positive infinity (IEEE 754).
class NumericAdd_FloatOverflowToInf {
    static void run() {

        float v = Float.MAX_VALUE + Float.MAX_VALUE;
        check(Float.isInfinite(v) && v > 0);
    }
}
