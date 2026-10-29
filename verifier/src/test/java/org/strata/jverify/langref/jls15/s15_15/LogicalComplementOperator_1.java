package org.strata.jverify.langref.jls15.s15_15;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.15.6 -- Logical Complement Operator !: !b yields the boolean negation of its operand.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_15.LogicalComplementOperator_1#run")
class LogicalComplementOperator_1 {
    static void run() {

        boolean t = true;
        check(!t == false);
    }
}
