package org.strata.jverify.langref.jls5.s5_6;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.6 -- Numeric Contexts: unary and binary numeric promotion applied to
// operands of numeric operators (unary -, +, *, /, % shifts, comparisons,
// conditional expression, etc.), exercising byte/short/char widening to int,
// and mixed int/long/float/double promotion to the wider type.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_6.NumericContexts_4#run")
class NumericContexts_4 {
    static void run() {

        // Binary numeric promotion: both operands promoted to the wider type.
        // int + long -> long
        int i = 3;
        long l = 4L;
        long sumIL = i + l;
        check(sumIL == 7L);
    }
}
