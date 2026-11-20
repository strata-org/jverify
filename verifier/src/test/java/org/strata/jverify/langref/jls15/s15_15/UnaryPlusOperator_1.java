package org.strata.jverify.langref.jls15.s15_15;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.3 -- Unary Plus Operator +: +x yields the (promoted) value of its operand unchanged.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_15.UnaryPlusOperator_1#run")
class UnaryPlusOperator_1 {
    static void run() {

        int x = 7;
        int p = +x;         // value unchanged
        check(p == 7);
    }
}
