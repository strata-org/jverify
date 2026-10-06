package org.strata.jverify.langref.jls5.s5_6;

import static org.strata.jverify.JVerify.*;

// JLS 5.6 -- Numeric Contexts: unary and binary numeric promotion applied to
// operands of numeric operators (unary -, +, *, /, % shifts, comparisons,
// conditional expression, etc.), exercising byte/short/char widening to int,
// and mixed int/long/float/double promotion to the wider type.
class NumericContexts_8 {
    static void run() {

        // int + float -> float
        float f = 2.0f;

        // long + double -> double
        double d = 1.5;

        // float + double -> double
        double sumFD = f + d;
        check(sumFD == 3.5);
    }
}
