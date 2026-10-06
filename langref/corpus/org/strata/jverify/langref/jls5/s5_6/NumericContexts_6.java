package org.strata.jverify.langref.jls5.s5_6;

import static org.strata.jverify.JVerify.*;

// JLS 5.6 -- Numeric Contexts: unary and binary numeric promotion applied to
// operands of numeric operators (unary -, +, *, /, % shifts, comparisons,
// conditional expression, etc.), exercising byte/short/char widening to int,
// and mixed int/long/float/double promotion to the wider type.
class NumericContexts_6 {
    static void run() {

        long l = 4L;

        // long + double -> double
        double d = 1.5;
        double sumLD = l + d;
        check(sumLD == 5.5);
    }
}
