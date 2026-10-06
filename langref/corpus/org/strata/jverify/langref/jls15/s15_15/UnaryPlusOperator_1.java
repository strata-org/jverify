package org.strata.jverify.langref.jls15.s15_15;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.3 -- Unary Plus Operator +: +x yields the (promoted) value of its operand unchanged.
class UnaryPlusOperator_1 {
    static void run() {

        int x = 7;
        int p = +x;         // value unchanged
        check(p == 7);
    }
}
