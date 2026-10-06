package org.strata.jverify.langref.jls15.s15_15;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.6 -- Logical Complement Operator !: !b yields the boolean negation of its operand.
class LogicalComplementOperator_2 {
    static void run() {

        check(!false == true);
    }
}
