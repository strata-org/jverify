package org.strata.jverify.langref.jls5.s5_6;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 5.6 -- Numeric Contexts: unary and binary numeric promotion applied to
// operands of numeric operators (unary -, +, *, /, % shifts, comparisons,
// conditional expression, etc.), exercising byte/short/char widening to int,
// and mixed int/long/float/double promotion to the wider type.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls5.s5_6.NumericContexts_3#run")
class NumericContexts_3 {
    static void run() {

        char c = 'A';               // 65
        int incC = c + 1;           // char promoted to int
        check(incC == 66);
    }
}
