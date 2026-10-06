package org.strata.jverify.langref.jls15.s15_15;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.3 -- Unary Plus Operator +: +x yields the (promoted) value of its operand unchanged.
class UnaryPlusOperator_2 {
    static void run() {

        // unary numeric promotion: byte operand promoted to int
        byte b = 3;
        int promoted = +b;
        check(promoted == 3);
    }
}
