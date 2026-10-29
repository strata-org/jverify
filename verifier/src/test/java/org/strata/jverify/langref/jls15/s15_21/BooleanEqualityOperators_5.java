package org.strata.jverify.langref.jls15.s15_21;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.2 -- Boolean Equality Operators == and !=: logical equality on boolean operands
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_21.BooleanEqualityOperators_5#run")
class BooleanEqualityOperators_5 {
    static void run() {

        // a == b is true iff a and b are both true or both false
        boolean p = (3 < 5);
        boolean q = (2 > 1);
        check(p == q);
    }
}
