package org.strata.jverify.langref.jls5.s5_6;

import static org.strata.jverify.JVerify.*;

// JLS 5.6 -- Numeric Contexts: unary and binary numeric promotion applied to
// operands of numeric operators (unary -, +, *, /, % shifts, comparisons,
// conditional expression, etc.), exercising byte/short/char widening to int,
// and mixed int/long/float/double promotion to the wider type.
class NumericContexts_10 {
    static void run() {

        // Binary numeric promotion: both operands promoted to the wider type.
        // int + long -> long
        int i = 3;
        long l = 4L;

        // Comparison operator applies binary numeric promotion.
        boolean cmp = i < l;        // int vs long -> long comparison
        check(cmp);
    }
}
