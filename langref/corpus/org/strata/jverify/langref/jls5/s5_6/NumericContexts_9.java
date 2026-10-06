package org.strata.jverify.langref.jls5.s5_6;

import static org.strata.jverify.JVerify.*;

// JLS 5.6 -- Numeric Contexts: unary and binary numeric promotion applied to
// operands of numeric operators (unary -, +, *, /, % shifts, comparisons,
// conditional expression, etc.), exercising byte/short/char widening to int,
// and mixed int/long/float/double promotion to the wider type.
class NumericContexts_9 {
    static void run() {

        // Binary numeric promotion: both operands promoted to the wider type.
        // int + long -> long
        int i = 3;

        // long + double -> double
        double d = 1.5;

        // Numeric context of a conditional expression's operands.
        int idx = 1;
        double cond = (idx > 0) ? i : d;   // int and double -> double
        check(cond == 3.0);
    }
}
